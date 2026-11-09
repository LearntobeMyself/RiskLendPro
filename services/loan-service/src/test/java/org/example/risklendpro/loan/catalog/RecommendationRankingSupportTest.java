package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationRankingSupportTest {

    @Test
    void contentSimilarityUsesCategoryTargetGroupAndTags() {
        LoanProduct left = product(1L, 1L, "PERSONAL_CREDIT", "[\"SALARIED\"]", 80);
        LoanProduct right = product(2L, 1L, "PERSONAL_CREDIT", "[\"SALARIED\"]", 70);
        double score = RecommendationRankingSupport.contentSimilarity(
                left, right, Set.of(1L, 2L), Set.of(1L, 2L));
        assertEquals(1.0, score, 0.0001);
    }

    @Test
    void preferenceScoreCombinesCategoryTargetAndTagWithCaps() {
        LoanProduct product = product(1L, 1L, "PERSONAL_CREDIT", "[\"SALARIED\"]", 50);
        UserPreferenceService.PreferenceProfile profile = new UserPreferenceService.PreferenceProfile(
                Map.of("PERSONAL_CREDIT", 20.0), Map.of("SALARIED", 8.0), Map.of(3L, 5.0));
        double score = RecommendationRankingSupport.preferenceScore(product, profile, Set.of(3L));
        assertEquals(1.0, score, 0.0001);
    }

    @Test
    void blendAppliesWeightsAndFatiguePenalty() {
        RecommendationRankingSupport.Weights weights = new RecommendationRankingSupport.Weights(
                0.45, 0.25, 0.20, 0.10);
        double score = RecommendationRankingSupport.blend(weights, 0.8, 0.8, 0.8, 0.8, 0.05);
        assertEquals(0.75, score, 0.0001);
    }

    @Test
    void diversityPrefersDifferentInstitutionsThenFillsOverflow() {
        LoanProduct first = product(1L, 10L, "PERSONAL_CREDIT", "[\"SALARIED\"]", 90);
        LoanProduct second = product(2L, 10L, "PERSONAL_CREDIT", "[\"SALARIED\"]", 80);
        LoanProduct third = product(3L, 20L, "BUSINESS", "[\"SELF_EMPLOYED\"]", 70);
        List<RecommendationRankingSupport.ScoredProduct> ranked = List.of(
                scored(first, 0.9), scored(second, 0.8), scored(third, 0.7));
        List<RecommendationRankingSupport.ScoredProduct> selected =
                RecommendationRankingSupport.selectDiverse(ranked, 2, 1, 2);
        assertEquals(2, selected.size());
        assertEquals(1L, selected.get(0).product().getId());
        assertEquals(3L, selected.get(1).product().getId());

        List<RecommendationRankingSupport.ScoredProduct> filled =
                RecommendationRankingSupport.selectDiverse(ranked, 3, 1, 2);
        assertEquals(3, filled.size());
        assertTrue(filled.stream().anyMatch(item -> item.product().getId() == 2L));
    }

    private static RecommendationRankingSupport.ScoredProduct scored(LoanProduct product, double score) {
        return new RecommendationRankingSupport.ScoredProduct(
                product, score, score, score, score, score, "test", List.of("TEST"));
    }

    private static LoanProduct product(Long id, Long institutionId, String category,
                                       String targetGroups, int sortWeight) {
        LoanProduct product = new LoanProduct();
        product.setId(id);
        product.setInstitutionId(institutionId);
        product.setCategoryCode(category);
        product.setTargetGroups(targetGroups);
        product.setSortWeight(sortWeight);
        return product;
    }
}
