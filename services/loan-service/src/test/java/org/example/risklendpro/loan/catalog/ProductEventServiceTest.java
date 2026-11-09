package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.dto.ImpressionRequest;
import org.example.risklendpro.loan.catalog.dto.ProductEventBatchRequest;
import org.example.risklendpro.loan.catalog.dto.ProductEventRequest;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.RecommendationImpression;
import org.example.risklendpro.loan.catalog.entity.UserProductEvent;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.example.risklendpro.loan.mapper.RecommendationImpressionMapper;
import org.example.risklendpro.loan.mapper.UserProductEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductEventServiceTest {

    @Mock UserProductEventMapper eventMapper;
    @Mock RecommendationImpressionMapper impressionMapper;
    @Mock LoanProductMapper productMapper;
    @Mock UserPreferenceService preferenceService;
    @Mock ProductPopularityService popularityService;

    private ProductEventService service;

    @BeforeEach
    void setUp() {
        service = new ProductEventService(
                eventMapper, impressionMapper, productMapper, preferenceService, popularityService);
    }

    @Test
    void clickEventUpdatesPreferenceAndPopularity() {
        LoanProduct product = product(7L);
        when(productMapper.selectById(7L)).thenReturn(product);
        ProductEventRequest event = event(7L, "CLICK");
        ProductEventBatchRequest batch = new ProductEventBatchRequest();
        batch.setEvents(List.of(event));

        Map<String, Object> result = service.ingest(1L, batch);

        assertEquals(1, result.get("accepted"));
        verify(eventMapper).insert(any(UserProductEvent.class));
        verify(popularityService).record(7L, "HOME", ProductEventType.CLICK);
        verify(preferenceService).record(1L, product, ProductEventType.CLICK);
    }

    @Test
    void duplicateExposureWithinWindowIsIgnored() {
        LoanProduct product = product(7L);
        when(productMapper.selectById(7L)).thenReturn(product);
        when(eventMapper.selectCount(any())).thenReturn(1L);
        ProductEventRequest event = event(7L, "EXPOSURE");
        ProductEventBatchRequest batch = new ProductEventBatchRequest();
        batch.setEvents(List.of(event));

        Map<String, Object> result = service.ingest(1L, batch);

        assertEquals(1, result.get("deduplicated"));
        verify(eventMapper, never()).insert(any(UserProductEvent.class));
        verify(popularityService, never()).record(any(), any(), any());
    }

    @Test
    void repeatedImpressionRequestIsIdempotent() {
        when(impressionMapper.selectById("req-1")).thenReturn(new RecommendationImpression());
        ImpressionRequest request = new ImpressionRequest();
        request.setRequestId("req-1");
        request.setScene("HOME");
        request.setProductIds(List.of(7L));

        Map<String, Object> result = service.recordImpressions(1L, request);

        assertTrue((Boolean) result.get("duplicate"));
        assertEquals(0, result.get("accepted"));
        verify(impressionMapper, never()).insert(any(RecommendationImpression.class));
        verify(eventMapper, never()).insert(any(UserProductEvent.class));
    }

    private static ProductEventRequest event(Long productId, String type) {
        ProductEventRequest event = new ProductEventRequest();
        event.setProductId(productId);
        event.setEventType(type);
        event.setScene("HOME");
        return event;
    }

    private static LoanProduct product(Long id) {
        LoanProduct product = new LoanProduct();
        product.setId(id);
        product.setStatus("ON_SHELF");
        product.setCategoryCode("PERSONAL_CREDIT");
        product.setTargetGroups("[\"SALARIED\"]");
        return product;
    }
}

