package com.storelense.rfid.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.storelense.rfid.processing", "com.storelense.common"})
@ConfigurationPropertiesScan({"com.storelense.rfid.processing", "com.storelense.common"})
public class RfidProcessingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RfidProcessingServiceApplication.class, args);
    }
}
