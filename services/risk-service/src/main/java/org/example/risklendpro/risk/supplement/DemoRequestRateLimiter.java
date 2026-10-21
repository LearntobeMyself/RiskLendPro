package org.example.risklendpro.risk.supplement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 匿名演示接口的轻量限流器（按客户端 key 的滑动窗口）。
 *
 * <p>演示接口不需要登录即可触发 OCR / AI 生成检测 / 篡改检测这类重计算，
 * 一旦被脚本刷就是白送算力，所以在接口层再加一道按 IP 的分钟级配额。
 * 单实例内存实现，够演示场景用；集群部署时应换成 Redis 计数。</p>
 */
@Component
public class DemoRequestRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    /** 客户端 key 数量超过该阈值时清理过期窗口，避免 Map 无界增长。 */
    private static final int PURGE_THRESHOLD = 1_000;

    private final Map<String, Deque<Long>> hitsByClient = new ConcurrentHashMap<>();
    private final int limitPerMinute;

    public DemoRequestRateLimiter(
            @Value("${security.demo.image-verify-rate-limit-per-minute:10}") int limitPerMinute) {
        this.limitPerMinute = Math.max(1, limitPerMinute);
    }

    /**
     * 尝试获取一次配额。
     *
     * @param clientKey 客户端标识，通常为 IP；为空时归入 unknown 桶，仍然受限流保护
     * @return true 表示本次允许，false 表示超出配额
     */
    public boolean tryAcquire(String clientKey) {
        long now = System.currentTimeMillis();
        purgeIfNeeded(now);
        String key = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey;
        Deque<Long> hits = hitsByClient.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() >= WINDOW_MILLIS) {
                hits.pollFirst();
            }
            if (hits.size() >= limitPerMinute) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }

    public int getLimitPerMinute() {
        return limitPerMinute;
    }

    private void purgeIfNeeded(long now) {
        if (hitsByClient.size() <= PURGE_THRESHOLD) {
            return;
        }
        hitsByClient.entrySet().removeIf(entry -> {
            Deque<Long> hits = entry.getValue();
            synchronized (hits) {
                while (!hits.isEmpty() && now - hits.peekFirst() >= WINDOW_MILLIS) {
                    hits.pollFirst();
                }
                return hits.isEmpty();
            }
        });
    }
}
