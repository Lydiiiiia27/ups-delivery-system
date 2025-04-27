package com.ups.config;

import com.ups.service.world.WorldResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for World Response processing
 */
@Configuration
public class WorldResponseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(WorldResponseConfig.class);
    
    private final WorldResponseHandler worldResponseHandler;
    
    @Autowired
    public WorldResponseConfig(WorldResponseHandler worldResponseHandler) {
        this.worldResponseHandler = worldResponseHandler;
    }
    
    /**
     * Create an executor for processing World Simulator responses
     * @return The configured executor
     */
    @Bean(name = "worldResponseExecutor")
    public Executor worldResponseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("WorldResponse-");
        executor.initialize();
        return executor;
    }
    
    /**
     * Create a thread for processing World Simulator responses
     * This thread will run the WorldResponseHandler.processResponses() method
     * @return The configured thread
     */
    @Bean
    public Thread worldResponseProcessorThread() {
        Thread thread = new Thread(() -> {
            try {
                logger.info("Starting World Response Processor thread");
                worldResponseHandler.processResponses();
            } catch (Exception e) {
                logger.error("Error in World Response Processor thread", e);
            }
        }, "WorldResponseProcessor");
        
        thread.setDaemon(true);
        return thread;
    }
}