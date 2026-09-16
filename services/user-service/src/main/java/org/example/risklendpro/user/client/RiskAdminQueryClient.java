package org.example.risklendpro.user.client;

import org.example.risklendpro.api.contract.RiskAdminQueryApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "risk-service", contextId = "riskAdminQueryClient", url = "${service.risk.url}")
public interface RiskAdminQueryClient extends RiskAdminQueryApi {
}
