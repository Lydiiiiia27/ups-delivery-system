package com.ups.config;

import com.ups.service.world.WorldResponseHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class WorldResponseConfig {
    
    private final WorldResponseHandler worldResponseHandler;
    
    @Autowired
    public WorldResponseConfig(WorldResponseHandler worldResponseHandler) {
        this.worldResponseHandler = worldResponseHandler;
    }
    
    @Bean(name = "worldResponseExecutor")
    public Executor worldResponseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("WorldResponse-");
        executor.initialize();
        return executor;
    }
    
    @Bean
    public Thread worldResponseProcessorThread() {
        Thread thread = new Thread(() -> {
            worldResponseHandler.processResponses();
        }, "WorldResponseProcessor");
        thread.setDaemon(true);
        return thread;
    }
}