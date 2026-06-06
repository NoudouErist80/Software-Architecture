package com.vibe.feedservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async thread pool configuration for the VIBE Feed Service.
 *
 * SCALING RATIONALE:
 * The original pool (core=4, max=20, queue=500) is appropriate for a
 * dev environment but will saturate under production load:
 * - max=20 means 20 concurrent async tasks; at peak traffic this starves
 *   Kafka consumers, scheduled jobs, and async notification dispatches.
 * - queue=500 silently absorbs traffic spikes but masks backpressure;
 *   if the queue fills, the CallerRunsPolicy ensures no tasks are dropped
 *   and instead applies natural backpressure on the calling thread.
 *
 * For billion-user scale, the JVM instances are horizontally scaled
 * (k8s pods), so per-instance pool sizes are intentionally moderate.
 * Tune ASYNC_CORE_POOL_SIZE and ASYNC_MAX_POOL_SIZE via environment variables
 * based on observed CPU core count in your deployment target.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * General-purpose async executor for @Async methods across the service.
     * Separate pools for Kafka consumers and scheduled tasks are managed by
     * Spring's own infrastructure beans.
     */
    @Bean(name = "vibeTaskExecutor")
    public Executor vibeTaskExecutor() {
        int corePoolSize = Integer.parseInt(
                System.getenv().getOrDefault("ASYNC_CORE_POOL_SIZE", "16"));
        int maxPoolSize = Integer.parseInt(
                System.getenv().getOrDefault("ASYNC_MAX_POOL_SIZE", "128"));
        int queueCapacity = Integer.parseInt(
                System.getenv().getOrDefault("ASYNC_QUEUE_CAPACITY", "2000"));

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("vibe-feed-async-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true); // reclaim idle core threads under low load

        // CallerRunsPolicy: if the pool + queue are both full, the submitting thread
        // executes the task itself. This provides natural backpressure rather than
        // silently dropping tasks or throwing RejectedExecutionException.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Graceful shutdown: wait for active tasks to complete before JVM exit
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("[AsyncConfig] vibeTaskExecutor initialized: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}