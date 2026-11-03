package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.dto.ImpressionRequest;
import org.example.risklendpro.loan.catalog.dto.ProductEventBatchRequest;
import org.example.risklendpro.loan.catalog.dto.ProductEventRequest;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.RecommendationImpression;
import org.example.risklendpro.loan.catalog.entity.UserProductEvent;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.example.risklendpro.loan.mapper.RecommendationImpressionMapper;
import org.example.risklendpro.loan.mapper.UserProductEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 行为上报、曝光去重、偏好更新与场景热度维护。 */
@Service
public class ProductEventService {

    private static final int MAX_BATCH_SIZE = 50;
    private static final int EXPOSURE_WINDOW_MINUTES = 5;

    private final UserProductEventMapper eventMapper;
    private final RecommendationImpressionMapper impressionMapper;
    private final LoanProductMapper productMapper;
    private final UserPreferenceService preferenceService;
    private final ProductPopularityService popularityService;

    public ProductEventService(UserProductEventMapper eventMapper,
                                RecommendationImpressionMapper impressionMapper,
                                LoanProductMapper productMapper,
                                UserPreferenceService preferenceService,
                                ProductPopularityService popularityService) {
        this.eventMapper = eventMapper;
        this.impressionMapper = impressionMapper;
        this.productMapper = productMapper;
        this.preferenceService = preferenceService;
        this.popularityService = popularityService;
    }

    @Transactional
    public Map<String, Object> ingest(Long userId, ProductEventBatchRequest batch) {
        if (batch == null || batch.getEvents() == null || batch.getEvents().isEmpty()) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "events 不能为空");
        }
        if (batch.getEvents().size() > MAX_BATCH_SIZE) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "单次最多上报50条事件");
        }
        int accepted = 0;
        int deduplicated = 0;
        for (ProductEventRequest event : batch.getEvents()) {
            if (recordOne(userId, event, null)) {
                accepted++;
            } else {
                deduplicated++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("deduplicated", deduplicated);
        return result;
    }

    /**
     * 推荐服务生成 requestId 后可由前端回传曝光；同一 requestId 重复提交会直接返回 duplicate=true。
     */
    @Transactional
    public Map<String, Object> recordImpressions(Long userId, ImpressionRequest request) {
        if (request == null || request.getProductIds() == null || request.getProductIds().isEmpty()) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "productIds 不能为空");
        }
        if (request.getProductIds().size() > MAX_BATCH_SIZE) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "单次最多记录50个曝光产品");
        }
        RecommendationScene scene = request.getScene() == null || request.getScene().isBlank()
                ? RecommendationScene.HOME : RecommendationScene.parse(request.getScene());
        String requestId = request.getRequestId() == null || request.getRequestId().isBlank()
                ? UUID.randomUUID().toString() : request.getRequestId().trim();
        if (requestId.length() > 64) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "requestId 最长64字符");
        }
        RecommendationImpression existing = impressionMapper.selectById(requestId);
        if (existing != null) {
            return Map.of("requestId", requestId, "accepted", 0, "duplicate", true);
        }
        RecommendationImpression impression = new RecommendationImpression();
        impression.setRequestId(requestId);
        impression.setUserId(userId);
        impression.setScene(scene.name());
        impression.setProductIds(request.getProductIds().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
        impression.setModelVersion(request.getModelVersion() == null ? "rule-v1" : request.getModelVersion());
        impression.setAbGroup(request.getAbGroup());
        impression.setCreatedAt(LocalDateTime.now());
        impressionMapper.insert(impression);

        LocalDateTime now = LocalDateTime.now();
        for (Long productId : request.getProductIds()) {
            if (productId == null || productMapper.selectById(productId) == null) {
                continue;
            }
            UserProductEvent event = new UserProductEvent();
            event.setUserId(userId);
            event.setProductId(productId);
            event.setEventType(ProductEventType.EXPOSURE.name());
            event.setScene(scene.name());
            event.setRequestId(requestId);
            event.setDedupeKey("impression:" + requestId + ":" + productId);
            event.setCreatedAt(now);
            try {
                eventMapper.insert(event);
            } catch (DuplicateKeyException ignored) {
                continue;
            }
            popularityService.record(productId, scene.name(), ProductEventType.EXPOSURE);
        }
        return Map.of("requestId", requestId, "accepted", request.getProductIds().size(), "duplicate", false);
    }

    private boolean recordOne(Long userId, ProductEventRequest request, String requestId) {
        if (request == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "事件不能为空");
        }
        ProductEventType type = ProductEventType.parse(request.getEventType());
        RecommendationScene scene = request.getScene() == null || request.getScene().isBlank()
                ? RecommendationScene.HOME : RecommendationScene.parse(request.getScene());
        LoanProduct product = request.getProductId() == null ? null : productMapper.selectById(request.getProductId());
        if (request.getProductId() != null && product == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_NOT_FOUND, "事件关联产品不存在");
        }
        if (type != ProductEventType.SEARCH && product == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "productId 不能为空");
        }
        if (request.getSessionId() != null && request.getSessionId().length() > 64) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "sessionId 最长64字符");
        }
        if (request.getKeyword() != null && request.getKeyword().length() > 100) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "keyword 最长100字符");
        }
        LocalDateTime now = LocalDateTime.now();
        String dedupeKey = null;
        if (type == ProductEventType.EXPOSURE && product != null) {
            long bucket = java.time.Instant.now().atZone(ZoneId.systemDefault()).toEpochSecond()
                    / (EXPOSURE_WINDOW_MINUTES * 60);
            dedupeKey = userId + ":" + product.getId() + ":" + scene.name() + ":" + bucket;
            Long count = eventMapper.selectCount(new LambdaQueryWrapper<UserProductEvent>()
                    .eq(UserProductEvent::getDedupeKey, dedupeKey));
            if (count != null && count > 0) {
                return false;
            }
        }
        UserProductEvent event = new UserProductEvent();
        event.setUserId(userId);
        event.setProductId(product == null ? null : product.getId());
        event.setEventType(type.name());
        event.setScene(scene.name());
        event.setSessionId(request.getSessionId());
        event.setKeyword(request.getKeyword());
        event.setMetadata(CatalogJson.writeMap(request.getMetadata()));
        event.setRequestId(requestId);
        event.setDedupeKey(dedupeKey);
        event.setCreatedAt(now);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            return false;
        }
        if (product != null) {
            popularityService.record(product.getId(), scene.name(), type);
            if (type != ProductEventType.EXPOSURE) {
                preferenceService.record(userId, product, type);
            }
        }
        return true;
    }
}
