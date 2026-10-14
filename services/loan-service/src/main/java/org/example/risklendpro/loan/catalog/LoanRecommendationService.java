package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.IdentityProofMaterials;
import org.example.risklendpro.api.dto.BorrowGateResult;
import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.LoanProductTag;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.example.risklendpro.loan.mapper.LoanProductTagMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 贷款产品推荐：授信通过弹窗推荐与成功页再推荐。推荐只展示，不代替申请校验。
 * 候选筛选（{@link LoanRecommendationSupport#eligible}）与卡片组装复用目录服务。
 */
@Service
public class LoanRecommendationService {

    private static final int MAX_ITEMS = 3;
    private static final Comparator<LoanProduct> RATE_ASC = Comparator.comparing(
            LoanProduct::getMinAnnualRate, Comparator.nullsLast(Comparator.naturalOrder()));

    private final LoanCatalogService loanCatalogService;
    private final LoanProductMapper productMapper;
    private final LoanProductTagMapper productTagMapper;
    private final RiskServiceClient riskServiceClient;
    private final UserCreditLimitMapper userCreditLimitMapper;
    private final LoanMapper loanMapper;

    public LoanRecommendationService(
            LoanCatalogService loanCatalogService,
            LoanProductMapper productMapper,
            LoanProductTagMapper productTagMapper,
            RiskServiceClient riskServiceClient,
            UserCreditLimitMapper userCreditLimitMapper,
            LoanMapper loanMapper) {
        this.loanCatalogService = loanCatalogService;
        this.productMapper = productMapper;
        this.productTagMapper = productTagMapper;
        this.riskServiceClient = riskServiceClient;
        this.userCreditLimitMapper = userCreditLimitMapper;
        this.loanMapper = loanMapper;
    }

    /** 授信通过弹窗推荐：候选最多 3 条，按 minAnnualRate 升序（null 靠后）。 */
    public Map<String, Object> recommendations(Long userId) {
        BorrowGateResult gate = riskServiceClient.getBorrowGate(userId);
        List<String> uploaded = riskServiceClient.listIdentityProofs(userId);

        List<LoanProduct> candidates = eligibleCandidates(
                ageOf(gate), carOf(gate), houseOf(gate), remainingLimit(userId), null, null);
        candidates.sort(RATE_ASC);
        List<LoanProduct> selected = candidates.stream().limit(MAX_ITEMS).toList();

        List<Map<String, Object>> cards = loanCatalogService.cardsOf(selected);
        for (int i = 0; i < selected.size(); i++) {
            cards.get(i).put("missingProofMaterials", missingProofs(selected.get(i), uploaded));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assessmentId", gate == null ? null : gate.assessmentId());
        result.put("items", cards);
        return result;
    }

    /**
     * 成功页再推荐：候选须同一分类且不是当前产品，按标签重合度降序、minAnnualRate 升序。
     * shouldRecommend 为客户对该产品成功放款笔数为 2 的倍数且 ≥2。
     */
    public Map<String, Object> postLoanRecommendations(Long userId, Long productId) {
        LoanProduct current = productMapper.selectById(productId);
        if (current == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_NOT_FOUND, "产品不存在");
        }
        long successCount = loanMapper.selectCount(new QueryWrapper<Loan>()
                .eq("user_id", userId)
                .eq("product_id", productId)
                .in("status", LoanStatusEnum.DISBURSED.getCode(), LoanStatusEnum.REPAID.getCode()));
        boolean shouldRecommend = successCount >= 2 && successCount % 2 == 0;

        BorrowGateResult gate = riskServiceClient.getBorrowGate(userId);
        List<LoanProduct> candidates = eligibleCandidates(
                ageOf(gate), carOf(gate), houseOf(gate), remainingLimit(userId),
                current.getCategoryCode(), productId);

        List<Long> lookupIds = new ArrayList<>(candidates.size() + 1);
        lookupIds.add(productId);
        for (LoanProduct candidate : candidates) {
            lookupIds.add(candidate.getId());
        }
        Map<Long, Set<Long>> tagsByProduct = tagIdsByProducts(lookupIds);
        Set<Long> currentTags = tagsByProduct.getOrDefault(productId, Set.of());
        candidates.sort((a, b) -> {
            int cmp = Long.compare(
                    overlap(tagsByProduct.getOrDefault(b.getId(), Set.of()), currentTags),
                    overlap(tagsByProduct.getOrDefault(a.getId(), Set.of()), currentTags));
            if (cmp != 0) {
                return cmp;
            }
            return RATE_ASC.compare(a, b);
        });
        List<LoanProduct> selected = candidates.stream().limit(MAX_ITEMS).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shouldRecommend", shouldRecommend);
        result.put("items", loanCatalogService.cardsOf(selected));
        return result;
    }

    private List<LoanProduct> eligibleCandidates(Integer age, Boolean hasCar, Boolean hasHouse,
                                                 BigDecimal remainingLimit, String categoryCode, Long excludeId) {
        return loanCatalogService.visibleProducts(categoryCode, excludeId).stream()
                .filter(product -> LoanRecommendationSupport.eligible(product, age, hasCar, hasHouse, remainingLimit))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Integer ageOf(BorrowGateResult gate) {
        return gate == null ? null : LoanRecommendationSupport.ageFromIdCard(gate.idCard());
    }

    private static Boolean carOf(BorrowGateResult gate) {
        return gate == null ? null : gate.hasCar();
    }

    private static Boolean houseOf(BorrowGateResult gate) {
        return gate == null ? null : gate.hasHouse();
    }

    private BigDecimal remainingLimit(Long userId) {
        UserCreditLimit limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
        return limit == null ? BigDecimal.ZERO : limit.getRemainingLimit();
    }

    /** 产品要求的证明材料类型中，用户尚未上传的部分（可能为空）。 */
    private static List<String> missingProofs(LoanProduct product, List<String> uploaded) {
        Set<String> required = IdentityProofMaterials.requiredCodes(
                CatalogJson.readStringList(product.getTargetGroups()));
        if (required.isEmpty()) {
            return List.of();
        }
        Set<String> uploadedCodes = uploaded == null ? Set.of() : new HashSet<>(uploaded);
        return required.stream().filter(code -> !uploadedCodes.contains(code)).sorted().toList();
    }

    private Map<Long, Set<Long>> tagIdsByProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> result = new HashMap<>();
        List<LoanProductTag> links = productTagMapper.selectList(
                new LambdaQueryWrapper<LoanProductTag>().in(LoanProductTag::getProductId, productIds));
        for (LoanProductTag link : links) {
            result.computeIfAbsent(link.getProductId(), key -> new HashSet<>()).add(link.getTagId());
        }
        return result;
    }

    private static long overlap(Set<Long> a, Set<Long> b) {
        Set<Long> copy = new HashSet<>(a);
        copy.retainAll(b);
        return copy.size();
    }
}
