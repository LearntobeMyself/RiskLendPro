package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.LoanProductTag;
import org.example.risklendpro.loan.catalog.entity.ProductSimilarity;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.example.risklendpro.loan.mapper.LoanProductTagMapper;
import org.example.risklendpro.loan.mapper.ProductSimilarityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 详情/浏览种子产品的相似召回；首次使用内容特征计算并落库。 */
@Service
public class ProductSimilarityService {

    private final ProductSimilarityMapper similarityMapper;
    private final LoanProductMapper productMapper;
    private final LoanProductTagMapper productTagMapper;

    public ProductSimilarityService(ProductSimilarityMapper similarityMapper,
                                    LoanProductMapper productMapper,
                                    LoanProductTagMapper productTagMapper) {
        this.similarityMapper = similarityMapper;
        this.productMapper = productMapper;
        this.productTagMapper = productTagMapper;
    }

    public Map<Long, Double> scores(Collection<Long> seedProductIds, List<LoanProduct> candidates) {
        if (seedProductIds == null || seedProductIds.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }
        Set<Long> seedIds = new HashSet<>(seedProductIds);
        Set<Long> candidateIds = new HashSet<>();
        for (LoanProduct candidate : candidates) {
            candidateIds.add(candidate.getId());
        }
        List<ProductSimilarity> stored = similarityMapper.selectList(new LambdaQueryWrapper<ProductSimilarity>()
                .in(ProductSimilarity::getProductId, seedIds)
                .in(ProductSimilarity::getSimilarProductId, candidateIds));
        if (!stored.isEmpty()) {
            Map<Long, Double> result = new HashMap<>();
            for (ProductSimilarity row : stored) {
                result.merge(row.getSimilarProductId(), row.getScore().doubleValue(), Math::max);
            }
            return result;
        }
        return computeAndStore(seedIds, candidates);
    }

    @Transactional
    protected Map<Long, Double> computeAndStore(Set<Long> seedIds, List<LoanProduct> candidates) {
        List<LoanProduct> products = new ArrayList<>();
        for (Long seedId : seedIds) {
            LoanProduct seed = productMapper.selectById(seedId);
            if (seed != null) {
                products.add(seed);
            }
        }
        products.addAll(candidates);
        Map<Long, Set<Long>> tags = tagIdsByProducts(products.stream().map(LoanProduct::getId).toList());
        Map<Long, Double> result = new HashMap<>();
        for (LoanProduct seed : products) {
            if (!seedIds.contains(seed.getId())) {
                continue;
            }
            for (LoanProduct candidate : candidates) {
                if (seed.getId().equals(candidate.getId())) {
                    continue;
                }
                double score = RecommendationRankingSupport.contentSimilarity(
                        seed, candidate, tags.getOrDefault(seed.getId(), Set.of()),
                        tags.getOrDefault(candidate.getId(), Set.of()));
                if (score <= 0) {
                    continue;
                }
                result.merge(candidate.getId(), score, Math::max);
                upsert(seed.getId(), candidate.getId(), score);
            }
        }
        return result;
    }

    private void upsert(Long productId, Long similarProductId, double score) {
        ProductSimilarity existing = similarityMapper.selectOne(new LambdaQueryWrapper<ProductSimilarity>()
                .eq(ProductSimilarity::getProductId, productId)
                .eq(ProductSimilarity::getSimilarProductId, similarProductId)
                .last("limit 1"));
        ProductSimilarity row = existing == null ? new ProductSimilarity() : existing;
        row.setProductId(productId);
        row.setSimilarProductId(similarProductId);
        row.setScore(BigDecimal.valueOf(score).setScale(6, RoundingMode.HALF_UP));
        row.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            similarityMapper.insert(row);
        } else {
            similarityMapper.updateById(row);
        }
    }

    private Map<Long, Set<Long>> tagIdsByProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> result = new HashMap<>();
        for (LoanProductTag link : productTagMapper.selectList(
                new LambdaQueryWrapper<LoanProductTag>().in(LoanProductTag::getProductId, productIds))) {
            result.computeIfAbsent(link.getProductId(), key -> new HashSet<>()).add(link.getTagId());
        }
        return result;
    }
}
