package cn.iocoder.yudao.module.devops.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * DevOps 异步任务配置。
 */
@Configuration
public class DevOpsAsyncConfiguration {

    /**
     * 构建日志读取线程池。
     *
     * <p>用于 {@link cn.iocoder.yudao.module.devops.framework.build.LocalBuildExecutor}
     * 异步读取子进程输出流。每个构建任务启动一个 reader 线程，流式回调 {@code LogSink}。
     *
     * <p>核心池大小=5，最大=20（支持 20 个并发构建），队列=100（突发场景）。
     * 线程空闲 60s 回收，拒绝策略=CallerRunsPolicy（降级到调用线程读取）。
     */
    @Bean("buildLogReaderExecutor")
    public Executor buildLogReaderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("build-log-reader-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

}
