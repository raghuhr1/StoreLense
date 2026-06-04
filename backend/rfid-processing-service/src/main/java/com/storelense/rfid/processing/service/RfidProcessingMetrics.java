package com.storelense.rfid.processing.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralised Micrometer metrics for the RFID processing pipeline.
 * All metrics use the prefix "rfid.processing".
 */
@Component
public class RfidProcessingMetrics {

    private final Counter eventsReceived;
    private final Counter eventsProcessed;
    private final Counter eventsDeduped;
    private final Counter decodeSuccess;
    private final Counter decodeFailure;
    private final Counter resolutionCacheHit;
    private final Counter resolutionCacheMiss;
    private final Counter resolutionFailed;
    private final Counter sohEventsPublished;
    private final Counter processingErrors;
    private final Timer   processingTimer;

    public RfidProcessingMetrics(MeterRegistry registry) {
        eventsReceived        = Counter.builder("rfid.processing.events.received")
                .description("Total RFID read events consumed from Kafka").register(registry);
        eventsProcessed       = Counter.builder("rfid.processing.events.processed")
                .description("Events successfully processed end-to-end").register(registry);
        eventsDeduped         = Counter.builder("rfid.processing.events.deduped")
                .description("Events skipped due to session-level deduplication").register(registry);
        decodeSuccess         = Counter.builder("rfid.processing.decode.success")
                .description("Successful SGTIN-96 EPC decodes").register(registry);
        decodeFailure         = Counter.builder("rfid.processing.decode.failure")
                .description("EPC decode attempts that failed or returned non-SGTIN").register(registry);
        resolutionCacheHit    = Counter.builder("rfid.processing.resolution.cache_hit")
                .description("EPC→product resolutions served from Redis cache").register(registry);
        resolutionCacheMiss   = Counter.builder("rfid.processing.resolution.cache_miss")
                .description("EPC→product resolutions requiring a product-service call").register(registry);
        resolutionFailed      = Counter.builder("rfid.processing.resolution.failed")
                .description("EPC→product resolutions that returned no match").register(registry);
        sohEventsPublished    = Counter.builder("rfid.processing.soh.events_published")
                .description("rfid.soh.updated events published to Kafka").register(registry);
        processingErrors      = Counter.builder("rfid.processing.errors")
                .description("Unhandled exceptions during event processing").register(registry);
        processingTimer       = Timer.builder("rfid.processing.duration")
                .description("End-to-end processing time per RFID read event")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordEventReceived()       { eventsReceived.increment(); }
    public void recordEventProcessed()      { eventsProcessed.increment(); }
    public void recordEventDeduped()        { eventsDeduped.increment(); }
    public void recordDecodeSuccess()       { decodeSuccess.increment(); }
    public void recordDecodeFailure()       { decodeFailure.increment(); }
    public void recordResolutionCacheHit()  { resolutionCacheHit.increment(); }
    public void recordResolutionCacheMiss() { resolutionCacheMiss.increment(); }
    public void recordResolutionFailed()    { resolutionFailed.increment(); }
    public void recordSohEventPublished()   { sohEventsPublished.increment(); }
    public void recordProcessingError()     { processingErrors.increment(); }

    public void recordProcessingTime(long nanos) {
        processingTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
}
