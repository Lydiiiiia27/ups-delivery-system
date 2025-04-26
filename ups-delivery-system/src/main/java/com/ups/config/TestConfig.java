package com.ups.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@Profile("test")
@PropertySource("classpath:application-test.properties")
public class TestConfig {

    @Autowired
    private Environment env;

    @Bean
    public String worldSimulatorHost() {
        // Default to localhost for local tests
        String host = env.getProperty("ups.world.host", "localhost");
        
        // Check if we're running in Docker
        if (System.getenv("DOCKER_ENV") != null) {
            // Use the Docker service name
            host = "world-simulator_server_1";
        }
        
        return host;
    }
}