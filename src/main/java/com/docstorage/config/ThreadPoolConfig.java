package com.docstorage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * <p>
 * 核心参数说明:
 * - corePoolSize: 核心线程数, 始终存活, 即使空闲也不销毁
 * - maxPoolSize: 最大线程数, 当队列满时才扩容到此值
 * - queueCapacity: 队列容量, 核心线程满后, 新任务进队列
 * - keepAliveSeconds: 非核心线程空闲存活时间
 * <p>
 * 线程数公式 (IO密集型): 线程数 = CPU核心数 * 2
 * 这里假设开发机8核, 生产可根据实际调整
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("docStorageExecutor")
    public Executor docStorageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("DocStorage-Async-");
        // 拒绝策略: 由调用线程处理 (避免任务丢失)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}