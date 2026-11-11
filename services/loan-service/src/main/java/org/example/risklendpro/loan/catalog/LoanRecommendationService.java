package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.IdentityProofMaterials;
import org.example.risklendpro.api.dto.BorrowGateResult;
import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.LoanProductTag;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.catalog.entity.UserProductEvent;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.example.risklendpro.loan.mapper.LoanProductTagMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.mapper.UserProductEventMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 获客推荐编排：资格硬过滤 -> 偏好/相似/热门/业务权重召回 -> 规则排序 -> 多样性重排 -> 曝光归因。
 * 推荐结果只用于展示，不替代产品详情和申请校验。
 */
@Service
public class LoanRecommendationService {

    public static final String MODEL_VERSION = "rule-v1.0";
    private static final int MAX_ITEMS = 10;

    /** 资格门槛缓存：同一用户的授信资格判定短时间内不变，缓存 30 秒消除跨服务 RPC。 */
    private static final long GATE_CACHE_TTL_MS = 30_000L;
    /** 身份证明材料清单缓存：用户已上传材料变更频率低，缓存 60 秒。 */
    private static final long PROOFS_CACHE_TTL_MS = 60_000L;
    /** 热门分 / 相似分缓存：全局共享数据，30 秒新鲜度窗口足够。 */
    private static final long SCORE_CACHE_TTL_MS = 30_000L;

    private final LoanCatalogService loanCatalogService;
    private final LoanProductMapper productMapper;
    private final LoanProductTagMapper productTagMapper;
    private final RiskServiceClient riskServiceClient;
    private final UserCreditLimitMapper userCreditLimitMapper;
    private final LoanMapper loanMapper;
    private final UserProductEventMapper eventMapper;
    private final UserPreferenceService preferenceService;
    private final ProductPopularityService popularityService;
    private final ProductSimilarityService similarityService;
    private final RecommendationConfigService configService;
    private final RecommendationCache cache;

    public LoanRecommendationService(
            LoanCatalogService loanCatalogService,
            LoanProductMapper productMapper,
            LoanProductTagMapper productTagMapper,
            RiskServiceClient riskServiceClient,
            UserCreditLimitMapper userCreditLimitMapper,
            LoanMapper loanMapper,
            UserProductEventMapper eventMapper,
            UserPreferenceService preferenceService,
            ProductPopularityService popularityService,
            ProductSimilarityService similarityService,
            RecommendationConfigService configService,
            RecommendationCache cache) {
        this.loanCatalogService = loanCatalogService;
        this.productMapper = productMapper;
        this.productTagMapper = productTagMapper;
        this.riskServiceClient = riskServiceClient;
        this.userCreditLimitMapper = userCreditLimitMapper;
        this.loanMapper = loanMapper;
        this.eventMapper = eventMapper;
        this.preferenceService = preferenceService;
        this.popularityService = popularityService;
        this.similarityService = similarityService;
        this.configService = configService;
        this.cache = cache;
    }

    /** 保持旧接口调用方式：默认授信通过场景。 */
    public Map<String, Object> recommendations(Long userId) {
        return recommendations(userId, null, null, null);
    }

    /** 四场景统一推荐入口。 */
    public Map<String, Object> recommendations(Long userId, String sceneValue, Long seedProductId, Integer limit) {
        RecommendationScene scene = RecommendationScene.parse(sceneValue);
        int targetLimit = limit == null ? scene.defaultLimit() : Math.max(1, Math.min(limit, MAX_ITEMS));
        String requestId = UUID.randomUUID().toString();
        String abGroup = abGroup(userId);
        RecommendationConfigService.SceneConfig config = configService.get(scene);

        BorrowGateResult gate = safeGate(userId);
        if (gate != null && !gate.allowed()) {
            return emptyResult(requestId, scene, gate.assessmentId(), abGroup,
                    "RISK_GATE_BLOCKED", gate.reasonMessage() == null ? "当前风险状态暂不建议申请产品" : gate.reasonMessage(),
                    true);
        }

        LoanProduct seedProduct = seedProductId == null ? null : productMapper.selectById(seedProductId);
        if (seedProductId != null && seedProduct == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_NOT_FOUND, "种子产品不存在");
        }
        String categoryFilter = scene == RecommendationScene.POST_LOAN && seedProduct != null
                ? seedProduct.getCategoryCode() : null;

        // 注意：这里刻意保持串行查询，不做 CompletableFuture 并行化。
        // 原因是 DB 在公网、主库连接池仅 12：并行化会让 20 个并发请求 × 8 个查询 = 160 个
        // 并发 DB 操作同时争抢连接池，触发大量 getConnection 阻塞，反而比串行更慢。
        // 真正的优化在于「减少查询次数」——通过下方 safeGate / safeIdentityProofs /
        // popularity / similarity 的短 TTL 缓存，把重复查询降到最低。
        List<LoanProduct> eligible = eligibleCandidates(
                ageOf(gate), carOf(gate), houseOf(gate),
                remainingLimit(userId), categoryFilter, seedProductId, userId);
        if (eligible.isEmpty()) {
            return emptyResult(requestId, scene, gate == null ? null : gate.assessmentId(), abGroup,
                    "NO_ELIGIBLE_PRODUCT", "暂无满足当前资格条件的产品", false);
        }

        List<String> uploaded = safeIdentityProofs(userId);
        // 偏好画像按用户缓存 60 秒：偏好是行为累积的慢变量，短 TTL 缓存不影响体验。
        String profileKey = "profile:" + userId;
        UserPreferenceService.PreferenceProfile profile = cache.get(profileKey);
        if (profile == null) {
            profile = preferenceService.profileMaps(userId);
            cache.put(profileKey, profile, 60_000L);
        }
        List<Long> recentProductIds = recentProductIds(userId, 10);

        Set<Long> seedIds = new LinkedHashSet<>();
        if (seedProductId != null) {
            seedIds.add(seedProductId);
        }
        if (scene == RecommendationScene.DETAIL || scene == RecommendationScene.HOME
                || scene == RecommendationScene.CREDIT_PASSED) {
            seedIds.addAll(recentProductIds);
        }

        Set<Long> productIds = new HashSet<>();
        for (LoanProduct product : eligible) {
            productIds.add(product.getId());
        }

        Map<Long, Set<Long>> tags = tagIdsByProducts(productIds);
        Map<Long, Double> popularityScores = RecommendationRankingSupport.normalize(
                popularityService.scores(scene.name(), productIds));
        Map<Long, Double> similarityScores = RecommendationRankingSupport.normalize(
                similarityService.scores(seedIds, eligible));
        Map<Long, Integer> exposureCounts = exposureCounts(userId,
                config.integer("fatigueDays", 7), productIds);

        double fatiguePenalty = config.decimal("fatiguePenalty", 0.10);
        RecommendationRankingSupport.Weights weights = new RecommendationRankingSupport.Weights(
                config.weight("similarity", 0.45), config.weight("popularity", 0.25),
                config.weight("preference", 0.20), config.weight("business", 0.10));
        boolean coldStart = profile.isEmpty() && recentProductIds.isEmpty();
        boolean fallbackUsed = similarityScores.isEmpty() && popularityScores.isEmpty()
                && (profile.isEmpty() || allPreferenceScoresZero(eligible, profile, tags));

        List<RecommendationRankingSupport.ScoredProduct> ranked = new ArrayList<>();
        for (LoanProduct product : eligible) {
            double similarity = similarityScores.getOrDefault(product.getId(), 0.0);
            double popularity = popularityScores.getOrDefault(product.getId(), 0.0);
            double preference = RecommendationRankingSupport.preferenceScore(
                    product, profile, tags.getOrDefault(product.getId(), Set.of()));
            double business = RecommendationRankingSupport.clamp(nz(product.getSortWeight()) / 100.0);
            int exposures = exposureCounts.getOrDefault(product.getId(), 0);
            double fatigue = exposures == 0 ? 0 : Math.min(fatiguePenalty, fatiguePenalty * exposures / 3.0);
            double score = RecommendationRankingSupport.blend(
                    weights, similarity, popularity, preference, business, fatigue);
            List<String> sources = recallSources(similarity, popularity, preference, business);
            ranked.add(new RecommendationRankingSupport.ScoredProduct(
                    product, score, similarity, popularity, preference, business,
                    reasonFor(similarity, popularity, preference, coldStart, fallbackUsed), sources));
        }

        int maxPerInstitution = config.integer("maxPerInstitution", 1);
        int maxPerCategory = config.integer("maxPerCategory", 2);
        List<RecommendationRankingSupport.ScoredProduct> selected = !config.enabled()
                ? businessOnly(eligible)
                : RecommendationRankingSupport.selectDiverse(ranked, targetLimit, maxPerInstitution, maxPerCategory);
        selected = applyExploration(userId, selected, RecommendationRankingSupport.selectDiverse(
                ranked, Math.min(ranked.size(), targetLimit + 5), maxPerInstitution, maxPerCategory),
                config.decimal("explorationRate", 0.10), targetLimit);

        List<LoanProduct> products = selected.stream()
                .map(RecommendationRankingSupport.ScoredProduct::product).toList();
        List<Map<String, Object>> cards = loanCatalogService.cardsOf(products);
        for (int i = 0; i < cards.size(); i++) {
            RecommendationRankingSupport.ScoredProduct scored = selected.get(i);
            Map<String, Object> card = cards.get(i);
            card.put("recommendationReason", scored.reason());
            card.put("recommendationScore", round(scored.score()));
            card.put("recallSources", scored.recallSources());
            card.put("missingProofMaterials", missingProofs(scored.product(), uploaded));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("scene", scene.name());
        result.put("modelVersion", MODEL_VERSION);
        result.put("abGroup", abGroup);
        result.put("assessmentId", gate == null ? null : gate.assessmentId());
        result.put("coldStart", coldStart);
        result.put("fallbackUsed", fallbackUsed);
        result.put("riskCheckDegraded", gate == null);
        result.put("items", cards);

        // ===== 性能优化：曝光归因下沉到前端异步上报 =====
        // 原实现在这里同步调用 eventService.recordImpressions，会对每个推荐产品做
        // selectById + event insert + 热度 update，每个请求同步写 10+ 次公网 DB，
        // 是获客推荐 P95 高达 6.5s 的根本原因（曝光写放大 + 占用连接池）。
        // 曝光归因是「后置统计」，与推荐结果无关；前端拿到 requestId 后通过
        // 已有的 POST /loan/products/impressions 端点异步上报即可（该端点幂等）。
        // 这里只保留 requestId 在响应中供前端回传，不再做服务端自动记录。
        return result;
    }

    /** 成功页再推荐，保留原有 shouldRecommend 字段。 */
    public Map<String, Object> postLoanRecommendations(Long userId, Long productId) {
        LoanProduct current = productMapper.selectById(productId);
        if (current == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_NOT_FOUND, "产品不存在");
        }
        long successCount = loanMapper.selectCount(new QueryWrapper<Loan>()
                .eq("user_id", userId)
                .eq("product_id", productId)
                .in("status", LoanStatusEnum.DISBURSED.getCode(), LoanStatusEnum.REPAID.getCode()));
        Map<String, Object> result = new LinkedHashMap<>(
                recommendations(userId, RecommendationScene.POST_LOAN.name(), productId, 3));
        result.put("shouldRecommend", successCount >= 2 && successCount % 2 == 0);
        return result;
    }

    private List<LoanProduct> eligibleCandidates(Integer age, Boolean hasCar, Boolean hasHouse,
                                                 BigDecimal remainingLimit, String categoryCode,
                                                 Long excludeId, Long userId) {
        Set<Long> applied = appliedProductIds(userId);
        // 产品目录（已上架且机构启用的产品）全局共享、短期不变，缓存 30 秒，
        // 用 computeIfAbsent 击穿保护：冷启动时 20 并发只允许 1 个穿透查库。
        String visibleKey = "visible:" + (categoryCode == null ? "all" : categoryCode)
                + ":" + (excludeId == null ? "none" : excludeId);
        List<LoanProduct> visible = cache.computeIfAbsent(visibleKey, 30_000L,
                () -> loanCatalogService.visibleProducts(categoryCode, excludeId));
        return visible.stream()
                .filter(product -> !applied.contains(product.getId()))
                .filter(product -> LoanRecommendationSupport.eligible(product, age, hasCar, hasHouse, remainingLimit))
                .toList();
    }

    private List<RecommendationRankingSupport.ScoredProduct> businessOnly(List<LoanProduct> products) {
        return products.stream()
                .sorted((a, b) -> Integer.compare(nz(b.getSortWeight()), nz(a.getSortWeight())))
                .map(product -> new RecommendationRankingSupport.ScoredProduct(
                        product, RecommendationRankingSupport.clamp(nz(product.getSortWeight()) / 100.0),
                        0, 0, 0, nz(product.getSortWeight()) / 100.0,
                        "推荐配置关闭，按产品运营权重展示", List.of("BUSINESS")))
                .toList();
    }

    private List<RecommendationRankingSupport.ScoredProduct> applyExploration(
            Long userId, List<RecommendationRankingSupport.ScoredProduct> selected,
            List<RecommendationRankingSupport.ScoredProduct> diversifiedPool,
            double explorationRate, int limit) {
        if (selected.isEmpty() || explorationRate <= 0 || diversifiedPool.size() <= limit) {
            return selected;
        }
        long bucket = Math.floorMod((userId == null ? 0 : userId) + LocalDate.now().toEpochDay(), 100);
        if (bucket >= explorationRate * 100) {
            return selected;
        }
        Set<Long> selectedIds = new HashSet<>();
        for (RecommendationRankingSupport.ScoredProduct item : selected) {
            selectedIds.add(item.product().getId());
        }
        RecommendationRankingSupport.ScoredProduct explore = diversifiedPool.stream()
                .filter(item -> !selectedIds.contains(item.product().getId()))
                .findFirst().orElse(null);
        if (explore == null) {
            return selected;
        }
        List<RecommendationRankingSupport.ScoredProduct> result = new ArrayList<>(selected.subList(0, Math.max(0, limit - 1)));
        result.add(new RecommendationRankingSupport.ScoredProduct(
                explore.product(), explore.score(), explore.similarity(), explore.popularity(),
                explore.preference(), explore.business(), "探索新选择：为你尝试不同产品",
                append(explore.recallSources(), "EXPLORATION")));
        return result;
    }

    private Set<Long> appliedProductIds(Long userId) {
        String key = "applied:" + userId;
        Set<Long> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<Loan> loans = loanMapper.selectList(new LambdaQueryWrapper<Loan>()
                .eq(Loan::getUserId, userId)
                .isNotNull(Loan::getProductId));
        Set<Long> result = new HashSet<>();
        for (Loan loan : loans) {
            if (loan.getProductId() != null) {
                result.add(loan.getProductId());
            }
        }
        cache.put(key, result, 30_000L);
        return result;
    }

    private List<Long> recentProductIds(Long userId, int limit) {
        String key = "recent:" + userId;
        List<Long> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<UserProductEvent> events = eventMapper.selectList(new LambdaQueryWrapper<UserProductEvent>()
                .eq(UserProductEvent::getUserId, userId)
                .in(UserProductEvent::getEventType,
                        ProductEventType.CLICK.name(), ProductEventType.FAVORITE.name(),
                        ProductEventType.DETAIL.name(), ProductEventType.APPLY_START.name())
                .isNotNull(UserProductEvent::getProductId)
                .orderByDesc(UserProductEvent::getCreatedAt)
                .last("limit " + limit));
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (UserProductEvent event : events) {
            ids.add(event.getProductId());
        }
        List<Long> result = new ArrayList<>(ids);
        cache.put(key, result, 30_000L);
        return result;
    }

    private Map<Long, Integer> exposureCounts(Long userId, int days, Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        String key = "exposure:" + userId;
        Map<Long, Integer> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<UserProductEvent> events = eventMapper.selectList(new LambdaQueryWrapper<UserProductEvent>()
                .eq(UserProductEvent::getUserId, userId)
                .eq(UserProductEvent::getEventType, ProductEventType.EXPOSURE.name())
                .ge(UserProductEvent::getCreatedAt, LocalDateTime.now().minusDays(Math.max(1, days)))
                .in(UserProductEvent::getProductId, productIds));
        Map<Long, Integer> result = new HashMap<>();
        for (UserProductEvent event : events) {
            if (event.getProductId() != null) {
                result.merge(event.getProductId(), 1, Integer::sum);
            }
        }
        cache.put(key, result, 30_000L);
        return result;
    }

    private Map<Long, Set<Long>> tagIdsByProducts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        String key = "tags:" + productIds.stream().sorted().map(String::valueOf)
                .reduce((a, b) -> a + "," + b).orElse("");
        Map<Long, Set<Long>> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<LoanProductTag> links = productTagMapper.selectList(
                new LambdaQueryWrapper<LoanProductTag>().in(LoanProductTag::getProductId, productIds));
        Map<Long, Set<Long>> result = new HashMap<>();
        for (LoanProductTag link : links) {
            result.computeIfAbsent(link.getProductId(), key2 -> new HashSet<>()).add(link.getTagId());
        }
        cache.put(key, result, 60_000L);
        return result;
    }

    private BigDecimal remainingLimit(Long userId) {
        String key = "limit:" + userId;
        BigDecimal cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        UserCreditLimit limit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
        BigDecimal result = limit == null ? BigDecimal.ZERO : limit.getRemainingLimit();
        cache.put(key, result, 30_000L);
        return result;
    }

    private BorrowGateResult safeGate(Long userId) {
        // 资格门槛按用户缓存 30 秒：同一用户短时间内的授信资格判定不变。
        // 用 computeIfAbsent 做击穿保护，避免冷启动/过期瞬间 20 并发同时穿透调 RPC。
        return cache.computeIfAbsent("gate:" + userId, GATE_CACHE_TTL_MS, () -> {
            try {
                return riskServiceClient.getBorrowGate(userId);
            } catch (RuntimeException e) {
                // RPC 失败时降级：返回 null 表示风控门禁不可用，继续走推荐（与原有语义一致）。
                // 注意：null 不写缓存，下次请求会重试。
                return null;
            }
        });
    }

    private List<String> safeIdentityProofs(Long userId) {
        String key = "proofs:" + userId;
        List<String> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> proofs;
        try {
            proofs = riskServiceClient.listIdentityProofs(userId);
        } catch (RuntimeException e) {
            return List.of();
        }
        List<String> result = proofs == null ? List.of() : proofs;
        cache.put(key, result, PROOFS_CACHE_TTL_MS);
        return result;
    }

    private Map<String, Object> emptyResult(String requestId, RecommendationScene scene, String assessmentId,
                                           String abGroup, String reasonCode, String message, boolean riskBlocked) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("scene", scene.name());
        result.put("modelVersion", MODEL_VERSION);
        result.put("abGroup", abGroup);
        result.put("assessmentId", assessmentId);
        result.put("coldStart", false);
        result.put("fallbackUsed", false);
        result.put("riskBlocked", riskBlocked);
        result.put("emptyReasonCode", reasonCode);
        result.put("emptyReason", message);
        result.put("items", List.of());
        return result;
    }

    private static List<String> recallSources(double similarity, double popularity, double preference, double business) {
        List<String> sources = new ArrayList<>();
        if (similarity > 0) sources.add("SIMILAR");
        if (popularity > 0) sources.add("POPULAR");
        if (preference > 0) sources.add("PREFERENCE");
        if (business > 0) sources.add("BUSINESS");
        if (sources.isEmpty()) sources.add("FALLBACK");
        return sources;
    }

    private static String reasonFor(double similarity, double popularity, double preference,
                                    boolean coldStart, boolean fallbackUsed) {
        if (similarity >= 0.4) return "与你最近浏览的产品相似";
        if (preference >= 0.45) return "符合你近期关注的方向";
        if (popularity > 0) return "同场景用户关注度较高";
        if (coldStart) return "新用户热门推荐";
        if (fallbackUsed) return "按额度、期限和利率综合匹配";
        return "综合额度与利率匹配";
    }

    private static boolean allPreferenceScoresZero(
            List<LoanProduct> products, UserPreferenceService.PreferenceProfile profile,
            Map<Long, Set<Long>> tags) {
        for (LoanProduct product : products) {
            if (RecommendationRankingSupport.preferenceScore(
                    product, profile, tags.getOrDefault(product.getId(), Set.of())) > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<String> append(List<String> source, String value) {
        List<String> result = new ArrayList<>(source);
        if (!result.contains(value)) result.add(value);
        return result;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String abGroup(Long userId) {
        long bucket = Math.floorMod((userId == null ? 0 : userId) + LocalDate.now().toEpochDay(), 100);
        return bucket < 50 ? "A" : "B";
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

    private static int nz(Integer value) {
        return value == null ? 0 : value;
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
}
