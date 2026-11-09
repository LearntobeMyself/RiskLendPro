package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.LoanProductTag;
import org.example.risklendpro.loan.catalog.entity.UserProductPreference;
import org.example.risklendpro.loan.mapper.LoanProductTagMapper;
import org.example.risklendpro.loan.mapper.UserProductPreferenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 将用户行为即时折叠为分类、目标人群、标签三维偏好，30 天半衰期。 */
@Service
public class UserPreferenceService {

    private static final double HALF_LIFE_DAYS = 30.0;
    private static final double MAX_SCORE = 100.0;

    private final UserProductPreferenceMapper preferenceMapper;
    private final LoanProductTagMapper productTagMapper;

    public UserPreferenceService(UserProductPreferenceMapper preferenceMapper,
                                 LoanProductTagMapper productTagMapper) {
        this.preferenceMapper = preferenceMapper;
        this.productTagMapper = productTagMapper;
    }

    @Transactional
    public void record(Long userId, LoanProduct product, ProductEventType eventType) {
        if (userId == null || product == null || product.getCategoryCode() == null) {
            return;
        }
        double weight = eventType.preferenceWeight();
        List<PreferenceDimension> dimensions = new ArrayList<>();
        dimensions.add(new PreferenceDimension(product.getCategoryCode(), "", 0L, weight));
        for (String targetGroup : CatalogJson.unique(CatalogJson.readStringList(product.getTargetGroups()))) {
            dimensions.add(new PreferenceDimension(product.getCategoryCode(), targetGroup, 0L, weight * 0.75));
        }
        List<LoanProductTag> tags = productTagMapper.selectList(
                new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getProductId, product.getId()));
        for (LoanProductTag tag : tags) {
            dimensions.add(new PreferenceDimension(product.getCategoryCode(), "", tag.getTagId(), weight * 0.60));
        }
        for (PreferenceDimension dimension : dimensions) {
            upsert(userId, dimension, LocalDateTime.now());
        }
    }

    public List<UserProductPreference> profile(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return preferenceMapper.selectList(new LambdaQueryWrapper<UserProductPreference>()
                .eq(UserProductPreference::getUserId, userId)
                .orderByDesc(UserProductPreference::getScore)
                .last("limit 100"));
    }

    public PreferenceProfile profileMaps(Long userId) {
        Map<String, Double> category = new HashMap<>();
        Map<String, Double> target = new HashMap<>();
        Map<Long, Double> tag = new HashMap<>();
        for (UserProductPreference item : profile(userId)) {
            double score = item.getScore() == null ? 0 : item.getScore().doubleValue();
            if (item.getTagId() != null && item.getTagId() > 0) {
                tag.put(item.getTagId(), score);
            } else if (item.getTargetGroup() != null && !item.getTargetGroup().isBlank()) {
                target.put(item.getTargetGroup(), score);
            } else if (item.getCategoryCode() != null && !item.getCategoryCode().isBlank()) {
                category.put(item.getCategoryCode(), score);
            }
        }
        return new PreferenceProfile(category, target, tag);
    }

    private void upsert(Long userId, PreferenceDimension dimension, LocalDateTime now) {
        UserProductPreference existing = preferenceMapper.selectOne(new LambdaQueryWrapper<UserProductPreference>()
                .eq(UserProductPreference::getUserId, userId)
                .eq(UserProductPreference::getCategoryCode, dimension.categoryCode())
                .eq(UserProductPreference::getTargetGroup, dimension.targetGroup())
                .eq(UserProductPreference::getTagId, dimension.tagId())
                .last("limit 1"));
        double decayed = existing == null ? 0 : decay(existing.getScore(), existing.getUpdatedAt(), now);
        double next = Math.min(MAX_SCORE, decayed + dimension.weight());
        UserProductPreference row = existing == null ? new UserProductPreference() : existing;
        row.setUserId(userId);
        row.setCategoryCode(dimension.categoryCode());
        row.setTargetGroup(dimension.targetGroup());
        row.setTagId(dimension.tagId());
        row.setScore(BigDecimal.valueOf(next).setScale(4, RoundingMode.HALF_UP));
        row.setUpdatedAt(now);
        if (existing == null) {
            preferenceMapper.insert(row);
        } else {
            preferenceMapper.updateById(row);
        }
    }

    private static double decay(BigDecimal score, LocalDateTime updatedAt, LocalDateTime now) {
        if (score == null) {
            return 0;
        }
        if (updatedAt == null) {
            return score.doubleValue();
        }
        double days = Math.max(0, Duration.between(updatedAt, now).toMinutes() / 1440.0);
        return score.doubleValue() * Math.pow(0.5, days / HALF_LIFE_DAYS);
    }

    private record PreferenceDimension(String categoryCode, String targetGroup, Long tagId, double weight) {
    }

    public record PreferenceProfile(Map<String, Double> categoryScores,
                                    Map<String, Double> targetGroupScores,
                                    Map<Long, Double> tagScores) {
        public boolean isEmpty() {
            return categoryScores.isEmpty() && targetGroupScores.isEmpty() && tagScores.isEmpty();
        }
    }
}
