package org.example.risklendpro.risk.client;

import org.example.risklendpro.api.contract.LoanQueryApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 贷款域行为/汇总契约（loan-service 实现）。
 * 与 {@link LoanServiceClient} 指向同一个 loan-service，Feign 客户端只支持单继承，故拆分。
 */
@FeignClient(name = "loan-service", contextId = "loanQueryClient", url = "${service.loan.url}")
public interface LoanQueryClient extends LoanQueryApi {
}
