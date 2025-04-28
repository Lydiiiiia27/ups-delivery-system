package com.ups.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class AmazonConnectionConfig {

    @Value("${amazon.service.url:http://amazon:8080}")
    private String defaultAmazonUrl;

    private final Environment environment;

    public AmazonConnectionConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public String amazonServiceUrl() {
        // Check environment variable first (highest priority)
        String envUrl = environment.getProperty("AMAZON_SERVICE_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return envUrl;
        }
        
        // Then check application properties
        return defaultAmazonUrl;
    }
} 