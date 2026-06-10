package com.storelense.erp.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class ErpRestClientConfig {

    private final ErpProperties erp;

    @Value("${storelense.product-service.base-url:http://product-service:8082}")
    private String productServiceBaseUrl;

    @Value("${storelense.product-service.service-token:}")
    private String productServiceToken;

    @Value("${storelense.soh-service.base-url:http://soh-service:8085}")
    private String sohServiceBaseUrl;

    @Value("${storelense.soh-service.service-token:}")
    private String sohServiceToken;

    @Primary
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
    public RestClient productRestClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(productServiceBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (productServiceToken != null && !productServiceToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + productServiceToken);
        }
        return builder.build();
    }

    @SuppressWarnings("null")
    @Bean
    public RestClient sohRestClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(sohServiceBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (sohServiceToken != null && !sohServiceToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + sohServiceToken);
        }
        return builder.build();
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
