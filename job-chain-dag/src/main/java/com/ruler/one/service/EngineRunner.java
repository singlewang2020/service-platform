package com.ruler.one.service;

import java.util.concurrent.Executor;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class EngineRunner {

    private final TaskExecutor executor;

    public EngineRunner() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("dag-engine-");
        ex.initialize();
        this.executor = ex;
    }

    public Executor executor() {
        return this.executor;
    }
}

