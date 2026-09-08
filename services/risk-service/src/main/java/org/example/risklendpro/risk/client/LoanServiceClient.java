package org.example.risklendpro.risk.client;

import org.example.risklendpro.api.contract.CreditLimitApi;
import org.example.risklendpro.api.contract.LoanQueryApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "loan-service", url = "${service.loan.url}")
public interface LoanServiceClient extends CreditLimitApi, LoanQueryApi {
}
