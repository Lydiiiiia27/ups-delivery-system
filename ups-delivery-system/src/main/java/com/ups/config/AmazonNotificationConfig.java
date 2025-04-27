package com.ups.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for Amazon notification services
 */
@Configuration
public class AmazonNotificationConfig {
    
    /**
     * Create a RestTemplate with appropriate timeout settings
     * @return The configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .requestFactory(this::clientHttpRequestFactory)
                .build();
    }
    
    /**
     * Create a ClientHttpRequestFactory with appropriate buffer sizes
     * @return The configured ClientHttpRequestFactory
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setOutputStreaming(false);
        factory.setBufferRequestBody(false);
        return factory;
    }
}