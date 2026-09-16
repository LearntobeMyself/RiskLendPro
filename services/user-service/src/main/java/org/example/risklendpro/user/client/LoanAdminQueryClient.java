package org.example.risklendpro.user.client;

import org.example.risklendpro.api.contract.LoanAdminQueryApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "loan-service", contextId = "loanAdminQueryClient", url = "${service.loan.url}")
public interface LoanAdminQueryClient extends LoanAdminQueryApi {
}
