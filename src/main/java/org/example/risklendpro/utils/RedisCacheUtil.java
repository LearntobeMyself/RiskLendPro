package org.example.risklendpro.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RedisCacheUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 缓存过期时间：1天
     */
    public static final long CACHE_EXPIRE_DAYS = 1;

    /**
     * 存入缓存
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value, Duration.ofDays(CACHE_EXPIRE_DAYS));
    }

    /**
     * 存入缓存（自定义过期时间）
     */
    public void set(String key, Object value, long days) {
        redisTemplate.opsForValue().set(key, value, Duration.ofDays(days));
    }

    /**
     * 获取缓存
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 检查缓存是否存在
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 生成风控报告缓存键
     */
    public static String getRiskReportKey(String applyId) {
        return "risk:report:" + applyId;
    }
}
