package org.example.risklendpro.loan.catalog;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 获客推荐链路专用的进程内短 TTL 缓存。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>只缓存「短期不变 / 高并发共享」的数据：资格门槛、身份证明材料、热门分、相似分。</li>
 *   <li>用进程内 ConcurrentHashMap + 过期时间戳，避免走公网 Redis 的额外网络往返
 *       （推荐是读多场景，进程内缓存命中延迟 &lt; 1µs，远优于 Redis 的 ~8ms 往返）。</li>
 *   <li>多实例部署时各实例缓存短期不一致可接受——这些数据本身有秒级新鲜度窗口。</li>
 *   <li>缓存 key 前缀区分业务域，便于监控与排查。</li>
 *   <li>提供 {@link #computeIfAbsent} 做「缓存击穿保护」：同一 key 过期瞬间，
 *       仅一个线程穿透去加载，其余线程等待该线程的结果，避免 20 个并发请求同时穿透打库。</li>
 * </ul>
 */
@Component
public class RecommendationCache {

    /** 每个 key 的缓存条目：值 + 写入时间戳。 */
    private static final class Entry {
        final Object value;
        final long expiresAtMillis;

        Entry(Object value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 每个 key 的加载锁：用于击穿保护，防止同一 key 并发穿透。 */
    private final Map<String, Object> loadLocks = new ConcurrentHashMap<>();

    /**
     * 读取缓存；命中且未过期时返回缓存值，否则返回 null。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis) {
            store.remove(key, entry);
            return null;
        }
        return (T) entry.value;
    }

    /**
     * 读取缓存，未命中时通过 loader 加载并写入缓存。
     *
     * <p>缓存击穿保护：同一 key 在过期瞬间只会有一个线程执行 loader，
     * 其余并发线程阻塞等待该线程的结果，避免大量请求同时穿透打库（是冷启动/过期时
     * P95 从 ~300ms 飙到 7s 的根因）。</p>
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, long ttlMillis, Supplier<T> loader) {
        T cached = get(key);
        if (cached != null) {
            return cached;
        }
        Object lock = loadLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            // 双重检查：拿到锁后可能已被其他线程填好
            T again = get(key);
            if (again != null) {
                return again;
            }
            T value = loader.get();
            put(key, value, ttlMillis);
            return value;
        }
    }

    /**
     * 写入缓存并设置 TTL（毫秒）。
     */
    public void put(String key, Object value, long ttlMillis) {
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * 主动失效某个 key。
     */
    public void evict(String key) {
        store.remove(key);
        loadLocks.remove(key);
    }

    /**
     * 清空所有缓存（产品/规则变更时调用）。
     */
    public void evictAll() {
        store.clear();
        loadLocks.clear();
    }
}
