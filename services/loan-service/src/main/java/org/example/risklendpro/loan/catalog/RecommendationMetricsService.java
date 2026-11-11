package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.loan.catalog.entity.UserProductEvent;
import org.example.risklendpro.loan.mapper.UserProductEventMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 管理端获客漏斗指标，按事件表实时聚合，便于演示和小数据量运营查看。 */
@Service
public class RecommendationMetricsService {

    private final UserProductEventMapper eventMapper;

    public RecommendationMetricsService(UserProductEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    public Map<String, Object> metrics(String sceneValue, Integer days) {
        RecommendationScene scene = sceneValue == null || sceneValue.isBlank()
                ? null : RecommendationScene.parse(sceneValue);
        int windowDays = days == null ? 7 : Math.max(1, Math.min(days, 90));
        LambdaQueryWrapper<UserProductEvent> wrapper = new LambdaQueryWrapper<UserProductEvent>()
                .ge(UserProductEvent::getCreatedAt, LocalDateTime.now().minusDays(windowDays));
        if (scene != null) {
            wrapper.eq(UserProductEvent::getScene, scene.name());
        }
        List<UserProductEvent> events = eventMapper.selectList(wrapper);
        long impressions = count(events, ProductEventType.EXPOSURE);
        long clicks = count(events, ProductEventType.CLICK)
                + count(events, ProductEventType.DETAIL)
                + count(events, ProductEventType.FAVORITE);
        long applies = count(events, ProductEventType.APPLY_START)
                + count(events, ProductEventType.APPLY_SUBMIT)
                + count(events, ProductEventType.APPLY_SUCCESS);
        long detailViews = count(events, ProductEventType.DETAIL);
        Set<Long> users = new HashSet<>();
        Map<Long, long[]> byProduct = new HashMap<>();
        for (UserProductEvent event : events) {
            if (event.getUserId() != null) {
                users.add(event.getUserId());
            }
            if (event.getProductId() == null) {
                continue;
            }
            long[] counts = byProduct.computeIfAbsent(event.getProductId(), key -> new long[3]);
            if (ProductEventType.EXPOSURE.name().equals(event.getEventType())) counts[0]++;
            if (ProductEventType.CLICK.name().equals(event.getEventType())
                    || ProductEventType.DETAIL.name().equals(event.getEventType())
                    || ProductEventType.FAVORITE.name().equals(event.getEventType())) counts[1]++;
            if (ProductEventType.APPLY_START.name().equals(event.getEventType())
                    || ProductEventType.APPLY_SUBMIT.name().equals(event.getEventType())
                    || ProductEventType.APPLY_SUCCESS.name().equals(event.getEventType())) counts[2]++;
        }
        List<Map<String, Object>> topProducts = new ArrayList<>();
        byProduct.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Long, long[]> e) -> e.getValue()[2] * 10 + e.getValue()[1])
                        .reversed())
                .limit(10)
                .forEach(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", entry.getKey());
                    item.put("impressions", entry.getValue()[0]);
                    item.put("clicks", entry.getValue()[1]);
                    item.put("applies", entry.getValue()[2]);
                    topProducts.add(item);
                });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scene", scene == null ? "ALL" : scene.name());
        result.put("days", windowDays);
        result.put("impressions", impressions);
        result.put("clicks", clicks);
        result.put("detailViews", detailViews);
        result.put("applies", applies);
        result.put("uniqueUsers", users.size());
        result.put("ctr", rate(clicks, impressions));
        result.put("clickThroughRate", rate(clicks, impressions));
        result.put("cvr", rate(applies, clicks));
        result.put("applyConversionRate", rate(applies, clicks));
        result.put("topProducts", topProducts);
        return result;
    }

    private static long count(List<UserProductEvent> events, ProductEventType type) {
        return events.stream().filter(event -> type.name().equals(event.getEventType())).count();
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return BigDecimal.valueOf((double) numerator / denominator)
                .setScale(6, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
