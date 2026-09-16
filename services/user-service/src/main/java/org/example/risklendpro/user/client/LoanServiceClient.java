package org.example.risklendpro.user.client;

import org.example.risklendpro.api.contract.LoanApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "loan-service", contextId = "loanServiceClient", url = "${service.loan.url}")
public interface LoanServiceClient extends LoanApi {
}
