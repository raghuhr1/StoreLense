package com.storelense.soh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.storelense.soh", "com.storelense.common"})
@ConfigurationPropertiesScan({"com.storelense.soh", "com.storelense.common"})
public class SohServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SohServiceApplication.class, args);
    }
}
