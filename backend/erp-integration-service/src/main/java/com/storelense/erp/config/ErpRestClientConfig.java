package com.storelense.erp.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class ErpRestClientConfig {

    private final ErpProperties erp;

    @Bean
    public RestClient erpRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) erp.connectTimeout().toMillis());
        factory.setReadTimeout((int) erp.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(erp.baseUrl() + "/" + erp.apiVersion())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Api-Key", erp.apiKey())
                .defaultHeader("X-Source-System", "StoreLense")
                .build();
    }

    @Bean
    public RetryConfig erpRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(erp.maxRetries())
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
    }

    @Bean
    public CircuitBreakerConfig erpCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(2))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
    }
}
