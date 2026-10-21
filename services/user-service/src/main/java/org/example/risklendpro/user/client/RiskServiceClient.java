package org.example.risklendpro.user.client;

import org.example.risklendpro.api.contract.RiskDecisionApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "risk-service", contextId = "riskServiceClient", url = "${service.risk.url}")
public interface RiskServiceClient extends RiskDecisionApi {
}
