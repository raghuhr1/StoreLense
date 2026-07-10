package com.storelense.rfid.processing.domain.repository;

import com.storelense.rfid.processing.domain.entity.AntennaLocationMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AntennaLocationMappingRepository extends JpaRepository<AntennaLocationMapping, UUID> {

    Optional<AntennaLocationMapping> findByReaderIdAndAntennaPortAndIsActiveTrue(
            String readerId, Short antennaPort);

    List<AntennaLocationMapping> findByStoreIdAndIsActiveTrueOrderByReaderIdAscAntennaPortAsc(
            UUID storeId);
}
