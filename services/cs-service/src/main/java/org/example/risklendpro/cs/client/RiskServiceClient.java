package org.example.risklendpro.cs.client;

import org.example.risklendpro.api.contract.RiskDecisionApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * risk-service 内部契约的 Feign 客户端，供 AI 客服实时查询用户授信评估状态与已上传身份证明材料。
 * 内部 token 由 common-runtime 的 InternalFeignRequestInterceptor 自动注入。
 */
@FeignClient(name = "risk-service", url = "${service.risk.url}")
public interface RiskServiceClient extends RiskDecisionApi {
}
