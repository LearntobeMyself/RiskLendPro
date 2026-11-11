package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanProduct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 推荐排序纯函数，便于单测和离线回放。 */
public final class RecommendationRankingSupport {

    private RecommendationRankingSupport() {
    }

    public static double contentSimilarity(LoanProduct left, LoanProduct right,
                                           Set<Long> leftTags, Set<Long> rightTags) {
        if (left == null || right == null) {
            return 0;
        }
        double score = 0;
        if (left.getCategoryCode() != null && left.getCategoryCode().equals(right.getCategoryCode())) {
            score += 0.45;
        }
        score += 0.30 * jaccard(
                new HashSet<>(CatalogJson.readStringList(left.getTargetGroups())),
                new HashSet<>(CatalogJson.readStringList(right.getTargetGroups())));
        score += 0.25 * jaccard(leftTags, rightTags);
        return clamp(score);
    }

    public static double preferenceScore(LoanProduct product,
                                         UserPreferenceService.PreferenceProfile profile,
                                         Set<Long> tags) {
        if (product == null || profile == null || profile.isEmpty()) {
            return 0;
        }
        double category = normalized(profile.categoryScores().get(product.getCategoryCode()),
                max(profile.categoryScores().values()));
        double target = 0;
        for (String targetGroup : CatalogJson.readStringList(product.getTargetGroups())) {
            target = Math.max(target, normalized(profile.targetGroupScores().get(targetGroup),
                    max(profile.targetGroupScores().values())));
        }
        double tag = 0;
        for (Long tagId : tags) {
            tag = Math.max(tag, normalized(profile.tagScores().get(tagId), max(profile.tagScores().values())));
        }
        return clamp(0.45 * category + 0.35 * target + 0.20 * tag);
    }

    public static double blend(Weights weights, double similarity, double popularity,
                               double preference, double business, double fatiguePenalty) {
        double total = weights.similarity() + weights.popularity() + weights.preference() + weights.business();
        if (total <= 0) {
            return 0;
        }
        double score = (weights.similarity() * clamp(similarity)
                + weights.popularity() * clamp(popularity)
                + weights.preference() * clamp(preference)
                + weights.business() * clamp(business)) / total;
        return clamp(score - Math.max(0, fatiguePenalty));
    }

    /**
     * 多样性重排：先按分数选，机构/分类超限的进入候选池；不足 limit 时再回填，
     * 因此小产品池不会被过严的规则误伤为空。
     */
    public static List<ScoredProduct> selectDiverse(List<ScoredProduct> ranked, int limit,
                                                    int maxPerInstitution, int maxPerCategory) {
        if (ranked == null || ranked.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<ScoredProduct> sorted = ranked.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();
        List<ScoredProduct> selected = new ArrayList<>();
        List<ScoredProduct> overflow = new ArrayList<>();
        Map<Long, Integer> institutionCount = new HashMap<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        for (ScoredProduct candidate : sorted) {
            LoanProduct product = candidate.product();
            Long institutionId = product.getInstitutionId();
            String categoryCode = product.getCategoryCode();
            boolean institutionOk = institutionId == null
                    || institutionCount.getOrDefault(institutionId, 0) < maxPerInstitution;
            boolean categoryOk = categoryCode == null
                    || categoryCount.getOrDefault(categoryCode, 0) < maxPerCategory;
            if (institutionOk && categoryOk && selected.size() < limit) {
                selected.add(candidate);
                if (institutionId != null) {
                    institutionCount.merge(institutionId, 1, Integer::sum);
                }
                if (categoryCode != null) {
                    categoryCount.merge(categoryCode, 1, Integer::sum);
                }
            } else {
                overflow.add(candidate);
            }
        }
        for (ScoredProduct candidate : overflow) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(candidate);
        }
        return selected;
    }

    public static Map<Long, Double> normalize(Map<Long, Double> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        double max = 0;
        for (double value : values.values()) {
            max = Math.max(max, value);
        }
        if (max <= 0) {
            return Map.of();
        }
        Map<Long, Double> result = new HashMap<>();
        for (Map.Entry<Long, Double> entry : values.entrySet()) {
            result.put(entry.getKey(), clamp(entry.getValue() / max));
        }
        return result;
    }

    public static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double normalized(Double value, double max) {
        return value == null || max <= 0 ? 0 : clamp(value / max);
    }

    private static double max(java.util.Collection<Double> values) {
        double max = 0;
        for (Double value : values) {
            if (value != null) {
                max = Math.max(max, value);
            }
        }
        return max;
    }

    private static double jaccard(Set<?> left, Set<?> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 0;
        }
        Set<Object> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<Object> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    public record Weights(double similarity, double popularity, double preference, double business) {
    }

    public record ScoredProduct(LoanProduct product, double score, double similarity,
                                double popularity, double preference, double business,
                                String reason, List<String> recallSources) {
    }
}
