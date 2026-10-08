package org.example.risklendpro.config;

import org.example.risklendpro.loan.catalog.CatalogAssembler;
import org.example.risklendpro.loan.catalog.entity.LoanInstitution;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void productDetailWithVerifiedAtRoundTripsThroughRedis() {
        LoanProduct product = new LoanProduct();
        product.setId(1L);
        product.setProductName("产品A");
        product.setCategoryCode("PERSONAL_CREDIT");
        product.setVerifiedAt(LocalDate.of(2026, 10, 8));
        LoanInstitution institution = new LoanInstitution();
        institution.setId(2L);
        institution.setInstitutionName("机构");
        institution.setInstitutionType("BANK");

        CatalogAssembler assembler = new CatalogAssembler(mock(StorageService.class));
        Map<String, Object> detail = assembler.productDetail(
                product, institution, null, List.of(), null, List.of(), false);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) detail.get("meta");
        assertEquals("2026-10-08", meta.get("verifiedAt"));

        GenericJackson2JsonRedisSerializer serializer = RedisConfig.jsonSerializer();
        Object restored = serializer.deserialize(serializer.serialize(detail));
        assertInstanceOf(Map.class, restored);
    }

    @Test
    void nestedLocalDateRoundTrips() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("verifiedAt", LocalDate.of(2026, 10, 8));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("meta", meta);

        GenericJackson2JsonRedisSerializer serializer = RedisConfig.jsonSerializer();
        Object restored = serializer.deserialize(serializer.serialize(detail));
        assertInstanceOf(Map.class, restored);
    }
}
