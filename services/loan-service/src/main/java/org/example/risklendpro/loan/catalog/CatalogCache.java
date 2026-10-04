package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.common.cache.RedisCacheUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CatalogCache {

    public static final String FILTERS_KEY = "catalog:filters";
    public static final String PRODUCT_KEY_PREFIX = "catalog:product:";

    private final RedisCacheUtil redisCacheUtil;

    public CatalogCache(RedisCacheUtil redisCacheUtil) {
        this.redisCacheUtil = redisCacheUtil;
    }

    public void putFilters(Object value) {
        redisCacheUtil.set(FILTERS_KEY, value, Duration.ofMinutes(5));
    }

    public <T> T getFilters(Class<T> type) {
        return redisCacheUtil.get(FILTERS_KEY, type);
    }

    public void putProduct(Long productId, Object value) {
        redisCacheUtil.set(PRODUCT_KEY_PREFIX + productId, value, Duration.ofMinutes(5));
    }

    public <T> T getProduct(Long productId, Class<T> type) {
        return redisCacheUtil.get(PRODUCT_KEY_PREFIX + productId, type);
    }

    public void evictAll() {
        redisCacheUtil.delete(FILTERS_KEY);
        redisCacheUtil.deleteByPattern(PRODUCT_KEY_PREFIX + "*");
    }
}
