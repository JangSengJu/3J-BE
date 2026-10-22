package com.jjj.jjj.domain.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AiConfig {

    @Bean(name = "aiExecutor")
    public ExecutorService aiExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "ai-sse");
            thread.setDaemon(true);
            return thread;
        });
    }
}
