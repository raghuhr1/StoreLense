package com.storelense.rfid.processing.service;

import com.storelense.rfid.processing.domain.entity.AntennaLocationMapping;
import com.storelense.rfid.processing.domain.repository.AntennaLocationMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationMappingServiceTest {

    @Mock AntennaLocationMappingRepository mappingRepository;
    @Mock StringRedisTemplate              redis;
    @Mock ValueOperations<String, String>  valueOps;

    @InjectMocks LocationMappingService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    private static final String READER_ID   = "reader-001";
    private static final Short  ANTENNA_1   = 1;
    private static final String CACHE_KEY   = LocationMappingService.CACHE_PREFIX + READER_ID + ":" + ANTENNA_1;

    // ── resolve: null guards ───────────────────────────────────────────────────

    @Test
    void resolve_returnsEmpty_whenReaderIdIsNull() {
        var result = service.resolve(null, ANTENNA_1);
        assertThat(result).isEmpty();
        verifyNoInteractions(redis, mappingRepository);
    }

    @Test
    void resolve_returnsEmpty_whenAntennaPortIsNull() {
        var result = service.resolve(READER_ID, null);
        assertThat(result).isEmpty();
        verifyNoInteractions(redis, mappingRepository);
    }

    // ── resolve: Redis cache hit ───────────────────────────────────────────────

    @Test
    void resolve_returnsCachedPair_whenCacheHit_withSection() {
        when(valueOps.get(CACHE_KEY)).thenReturn("SALES_FLOOR|WOMENS");

        var result = service.resolve(READER_ID, ANTENNA_1);

        assertThat(result).isPresent();
        assertThat(result.get().locationCode()).isEqualTo("SALES_FLOOR");
        assertThat(result.get().sectionCode()).isEqualTo("WOMENS");
        verifyNoInteractions(mappingRepository);
    }

    @Test
    void resolve_returnsCachedPair_whenCacheHit_withoutSection() {
        when(valueOps.get(CACHE_KEY)).thenReturn("BACKROOM|");

        var result = service.resolve(READER_ID, ANTENNA_1);

        assertThat(result).isPresent();
        assertThat(result.get().locationCode()).isEqualTo("BACKROOM");
        assertThat(result.get().sectionCode()).isNull();
        verifyNoInteractions(mappingRepository);
    }

    @Test
    void resolve_returnsEmpty_whenNotFoundSentinelCached() {
        when(valueOps.get(CACHE_KEY)).thenReturn(LocationMappingService.NOT_FOUND);

        var result = service.resolve(READER_ID, ANTENNA_1);

        assertThat(result).isEmpty();
        verifyNoInteractions(mappingRepository);
    }

    // ── resolve: cache miss → DB hit ──────────────────────────────────────────

    @Test
    void resolve_queriesDatabase_andCachesResult_whenCacheMissAndMappingExists() {
        when(valueOps.get(CACHE_KEY)).thenReturn(null);

        AntennaLocationMapping mapping = new AntennaLocationMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setReaderId(READER_ID);
        mapping.setAntennaPort(ANTENNA_1);
        mapping.setLocationCode("SALES_FLOOR");
        mapping.setSectionCode("MENS");
        mapping.setIsActive(true);

        when(mappingRepository.findByReaderIdAndAntennaPortAndIsActiveTrue(READER_ID, ANTENNA_1))
                .thenReturn(Optional.of(mapping));

        var result = service.resolve(READER_ID, ANTENNA_1);

        assertThat(result).isPresent();
        assertThat(result.get().locationCode()).isEqualTo("SALES_FLOOR");
        assertThat(result.get().sectionCode()).isEqualTo("MENS");

        // Verify cache was populated with correct value and TTL
        ArgumentCaptor<String>   keyCaptor  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String>   valCaptor  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor  = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCaptor.capture(), valCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo(CACHE_KEY);
        assertThat(valCaptor.getValue()).isEqualTo("SALES_FLOOR|MENS");
        assertThat(ttlCaptor.getValue()).isEqualTo(LocationMappingService.TTL);
    }

    @Test
    void resolve_cachesBackroomWithoutSection() {
        when(valueOps.get(CACHE_KEY)).thenReturn(null);

        AntennaLocationMapping mapping = new AntennaLocationMapping();
        mapping.setReaderId(READER_ID);
        mapping.setAntennaPort(ANTENNA_1);
        mapping.setLocationCode("BACKROOM");
        mapping.setSectionCode(null);
        mapping.setIsActive(true);

        when(mappingRepository.findByReaderIdAndAntennaPortAndIsActiveTrue(READER_ID, ANTENNA_1))
                .thenReturn(Optional.of(mapping));

        var result = service.resolve(READER_ID, ANTENNA_1);

        assertThat(result).isPresent();
        assertThat(result.get().locationCode()).isEqualTo("BACKROOM");
        assertThat(result.get().sectionCode()).isNull();

        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(CACHE_KEY), valCaptor.capture(), any(Duration.class));
        assertThat(valCaptor.getValue()).isEqualTo("BACKROOM|");
    }

    // ── resolve: cache miss → DB miss → negative cache ────────────────────────

    @Test
    void resolve_cachesNotFoundSentinel_whenMappingNotInDatabase() {
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(mappingRepository.findByReaderIdAndAntennaPortAndIsActiveTrue(READER_ID, ANTENNA_1))
                .thenReturn(Optional.empty());

        var result = service.resolve(READER_ID, ANTENNA_1);

        assertThat(result).isEmpty();

        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(CACHE_KEY), valCaptor.capture(), eq(LocationMappingService.TTL));
        assertThat(valCaptor.getValue()).isEqualTo(LocationMappingService.NOT_FOUND);
    }

    // ── evict ──────────────────────────────────────────────────────────────────

    @Test
    void evict_deletesCorrectCacheKey() {
        service.evict(READER_ID, ANTENNA_1);
        verify(redis).delete(CACHE_KEY);
    }

    @Test
    void evict_doesNotInteractWithRepository() {
        service.evict(READER_ID, ANTENNA_1);
        verifyNoInteractions(mappingRepository);
    }

    // ── LocationPair record ────────────────────────────────────────────────────

    @Test
    void locationPair_equalityByValue() {
        var p1 = new LocationMappingService.LocationPair("SALES_FLOOR", "MENS");
        var p2 = new LocationMappingService.LocationPair("SALES_FLOOR", "MENS");
        var p3 = new LocationMappingService.LocationPair("BACKROOM",    null);

        assertThat(p1).isEqualTo(p2);
        assertThat(p1).isNotEqualTo(p3);
    }
}
