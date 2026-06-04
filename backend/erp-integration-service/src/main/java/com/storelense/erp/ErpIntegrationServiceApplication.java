package com.storelense.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.storelense.erp", "com.storelense.common"})
@ConfigurationPropertiesScan({"com.storelense.erp", "com.storelense.common"})
@EnableScheduling
public class ErpIntegrationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ErpIntegrationServiceApplication.class, args);
    }
}
