package com.storelense.erp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "storelense.erp.import.s3-enabled", havingValue = "true")
public class S3Config {

    @Bean
    public S3Client s3Client(ErpImportProperties props) {
        return S3Client.builder()
                .region(Region.of(props.s3Region()))
                .build();
    }
}
