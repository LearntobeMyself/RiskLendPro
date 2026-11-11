package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.loan.catalog.entity.ProductPopularity;
import org.example.risklendpro.loan.mapper.ProductPopularityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 维护场景级曝光、点击、申请计数与转化率。 */
@Service
public class ProductPopularityService {

    private final ProductPopularityMapper popularityMapper;
    private final RecommendationCache cache;

    public ProductPopularityService(ProductPopularityMapper popularityMapper,
                                    RecommendationCache cache) {
        this.popularityMapper = popularityMapper;
        this.cache = cache;
    }

    @Transactional
    public void record(Long productId, String scene, ProductEventType type) {
        if (productId == null || scene == null || type == null) {
            return;
        }
        ProductPopularity row = popularityMapper.selectOne(new LambdaQueryWrapper<ProductPopularity>()
                .eq(ProductPopularity::getProductId, productId)
                .eq(ProductPopularity::getScene, scene)
                .last("limit 1"));
        if (row == null) {
            row = new ProductPopularity();
            row.setProductId(productId);
            row.setScene(scene);
            row.setImpressions(0L);
            row.setClicks(0L);
            row.setApplies(0L);
        }
        if (type == ProductEventType.EXPOSURE) {
            row.setImpressions(row.getImpressions() + 1);
        } else if (type == ProductEventType.CLICK || type == ProductEventType.DETAIL
                || type == ProductEventType.FAVORITE) {
            row.setClicks(row.getClicks() + 1);
        } else if (type.countsAsApply()) {
            row.setApplies(row.getApplies() + 1);
        }
        updateRates(row);
        row.setUpdatedAt(LocalDateTime.now());
        if (row.getId() == null) {
            popularityMapper.insert(row);
        } else {
            popularityMapper.updateById(row);
        }
    }

    public Map<Long, Double> scores(String scene, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        // 热门分是场景级全局共享数据，按场景 + 产品集缓存 30 秒，避免每次推荐都全量查库。
        // key 使用排序后的产品 id 拼接，确保不同产品集不会互相污染。
        String cacheKey = "pop:" + scene + ":" + productIds.stream().sorted().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("");
        Map<Long, Double> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<ProductPopularity> rows = popularityMapper.selectList(new LambdaQueryWrapper<ProductPopularity>()
                .eq(ProductPopularity::getScene, scene)
                .in(ProductPopularity::getProductId, productIds));
        Map<Long, Double> result = new HashMap<>();
        for (ProductPopularity row : rows) {
            result.put(row.getProductId(), row.getScore() == null ? 0 : row.getScore().doubleValue());
        }
        cache.put(cacheKey, result, 30_000L);
        return result;
    }

    private static void updateRates(ProductPopularity row) {
        long impressions = row.getImpressions() == null ? 0 : row.getImpressions();
        long clicks = row.getClicks() == null ? 0 : row.getClicks();
        long applies = row.getApplies() == null ? 0 : row.getApplies();
        double ctr = impressions == 0 ? 0 : (double) clicks / impressions;
        double cvr = clicks == 0 ? 0 : (double) applies / clicks;
        double score = 0.45 * ctr + 0.45 * cvr + 0.10 * Math.log1p(impressions) / 10.0;
        row.setCtr(BigDecimal.valueOf(ctr).setScale(6, RoundingMode.HALF_UP));
        row.setCvr(BigDecimal.valueOf(cvr).setScale(6, RoundingMode.HALF_UP));
        row.setScore(BigDecimal.valueOf(score).setScale(6, RoundingMode.HALF_UP));
    }
}
