package org.example.risklendpro.common.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内登录失败限制器：同一账号（手机号/用户名）连续失败达阈值后临时锁定。
 * 单实例部署下足以拦截暴力破解；多实例部署时应替换为 Redis 实现。
 */
@Component
public class LoginAttemptLimiter {

    /** 锁定前允许的最大连续失败次数 */
    private static final int MAX_ATTEMPTS = 5;
    /** 失败计数窗口（毫秒）：窗口内累计失败，超窗口重新计数 */
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    /** 触发锁定后的锁定期（毫秒） */
    private static final long LOCK_MILLIS = 15 * 60 * 1000L;

    private record FailState(AtomicLong count, AtomicLong windowStart, AtomicLong lockUntil) {
    }

    private final Map<String, FailState> states = new ConcurrentHashMap<>();

    /** 登录前调用：若账号处于锁定期则抛出业务异常（映射为 400）。 */
    public void checkAllowed(String key) {
        FailState state = key == null ? null : states.get(key);
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long lockUntil = state.lockUntil().get();
        if (now < lockUntil) {
            long remainMinutes = Math.max(1, (lockUntil - now) / 60000);
            throw new RuntimeException("登录失败次数过多，账号已临时锁定，请 " + remainMinutes + " 分钟后再试");
        }
    }

    /** 凭证校验失败时调用：累计失败次数，达阈值后锁定。 */
    public void onFailure(String key) {
        if (key == null) {
            return;
        }
        long now = System.currentTimeMillis();
        FailState state = states.computeIfAbsent(key,
                k -> new FailState(new AtomicLong(), new AtomicLong(now), new AtomicLong()));
        if (now < state.lockUntil().get()) {
            return;
        }
        long windowStart = state.windowStart().get();
        if (now - windowStart > WINDOW_MILLIS) {
            // 窗口过期：重新计数（仅重置一次即可，竞争下重复 reset 幂等）
            state.windowStart().set(now);
            state.count().set(0);
        }
        long attempts = state.count().incrementAndGet();
        if (attempts >= MAX_ATTEMPTS) {
            state.lockUntil().set(now + LOCK_MILLIS);
            state.count().set(0);
            state.windowStart().set(now);
        }
    }

    /** 登录成功时调用：清除失败记录。 */
    public void onSuccess(String key) {
        if (key != null) {
            states.remove(key);
        }
    }
}
