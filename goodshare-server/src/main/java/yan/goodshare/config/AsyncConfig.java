package yan.goodshare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AsyncConfig {

    /**
     * 推荐服务专用线程池，仅用于 Phase 2 并行调用 CF(Python微服务) 和 ES(内容推荐)。
     * 使用 SynchronousQueue（无缓冲队列）：任务不排队，直接创建线程执行，
     * 避免 ThreadPoolExecutor 的"先入队后扩线程"陷阱导致实际并发低下。
     * 核心线程 0，最大线程 200，空闲 60s 回收，CallerRunsPolicy 兜底。
     */
    @Bean("recommendationExecutor")
    public Executor recommendationExecutor() {
        return new ThreadPoolExecutor(
                0, 200,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
