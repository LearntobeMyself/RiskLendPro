package org.example.risklendpro.risk.supplement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 匿名 Demo 接口的限流器：同一客户端超过每分钟配额必须被拒绝，不同客户端互不影响。
 */
class DemoRequestRateLimiterTest {

    @Test
    void allowsWithinQuotaAndBlocksBeyondIt() {
        DemoRequestRateLimiter limiter = new DemoRequestRateLimiter(3);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"), "第 4 次应被限流");
    }

    @Test
    void isolatesClients() {
        DemoRequestRateLimiter limiter = new DemoRequestRateLimiter(1);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.2"), "另一个 IP 不应被第一个 IP 的配额影响");
    }

    @Test
    void unknownClientStillLimited() {
        DemoRequestRateLimiter limiter = new DemoRequestRateLimiter(1);

        assertTrue(limiter.tryAcquire(null));
        assertFalse(limiter.tryAcquire(""), "空 key 归入 unknown 桶，同样受限");
    }

    @Test
    void nonPositiveQuotaFallsBackToOne() {
        DemoRequestRateLimiter limiter = new DemoRequestRateLimiter(0);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));
    }
}
