package com.storelense.refill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.storelense.refill", "com.storelense.common"})
@ConfigurationPropertiesScan({"com.storelense.refill", "com.storelense.common"})
public class RefillServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RefillServiceApplication.class, args);
    }
}
