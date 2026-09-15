package org.example.risklendpro.risk.client;

import org.example.risklendpro.api.contract.LoanApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "loan-service", url = "${service.loan.url}")
public interface LoanServiceClient extends LoanApi {
}
