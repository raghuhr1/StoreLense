package com.storelense.rfid.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.storelense.rfid.ingest", "com.storelense.common"})
@ConfigurationPropertiesScan({"com.storelense.rfid.ingest", "com.storelense.common"})
public class RfidIngestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RfidIngestServiceApplication.class, args);
    }
}
