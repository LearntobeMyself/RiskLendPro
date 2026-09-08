package org.example.risklendpro.loan.client;

import org.example.risklendpro.api.contract.RiskDecisionApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "risk-service", url = "${service.risk.url}")
public interface RiskServiceClient extends RiskDecisionApi {
}
