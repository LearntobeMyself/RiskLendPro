package org.example.risklendpro.risk.client;

import org.example.risklendpro.api.contract.RiskDecisionApi;
import org.example.risklendpro.api.dto.LimitMultiplierDecision;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 A0 契约测试：额度系数查询必须携带 userId，并返回含
 * dataStatus / decisionAllowed / modelVersion 的决策对象；
 * 不允许再暴露无 userId 的普通系数接口给额度流程。
 */
class RiskDecisionApiContractTest {

    @Test
    void anonymousLimitMultiplierEndpointMustNotExist() {
        boolean anonymous = Arrays.stream(RiskDecisionApi.class.getMethods())
                .anyMatch(m -> "getLimitMultiplier".equals(m.getName()));
        assertFalse(anonymous, "RiskDecisionApi 不应再暴露无 userId 的额度系数接口");
    }

    @Test
    void limitMultiplierEndpointRequiresUserIdAndReturnsDecisionObject() {
        Method method = Arrays.stream(RiskDecisionApi.class.getMethods())
                .filter(m -> "getLimitMultiplierForUser".equals(m.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(method, "缺少 getLimitMultiplierForUser 契约方法");
        assertEquals(LimitMultiplierDecision.class, method.getReturnType());

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping, "额度系数接口必须声明 GetMapping");
        assertTrue(String.join(",", mapping.value()).contains("{userId}"),
                "额度系数接口路径必须携带 userId，实际=" + String.join(",", mapping.value()));
        assertTrue(String.join(",", mapping.value()).contains("limit-multiplier"));

        boolean hasUserIdPath = Arrays.stream(method.getParameters())
                .anyMatch(p -> p.isAnnotationPresent(PathVariable.class) && Long.class.equals(p.getType()));
        assertTrue(hasUserIdPath, "额度系数接口必须包含 Long 类型的 userId 路径参数");
    }

    @Test
    void decisionObjectCarriesAuditFields() {
        assertNotNull(field(LimitMultiplierDecision.class, "dataStatus"));
        assertNotNull(field(LimitMultiplierDecision.class, "decisionAllowed"));
        assertNotNull(field(LimitMultiplierDecision.class, "modelVersion"));
        assertNotNull(field(LimitMultiplierDecision.class, "featureVersion"));
        assertNotNull(field(LimitMultiplierDecision.class, "policyVersion"));
        assertNotNull(field(LimitMultiplierDecision.class, "ruleHash"));
        assertNotNull(field(LimitMultiplierDecision.class, "multiplier"));
        assertNotNull(field(LimitMultiplierDecision.class, "scoreReliable"));
    }

    private static Object field(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}