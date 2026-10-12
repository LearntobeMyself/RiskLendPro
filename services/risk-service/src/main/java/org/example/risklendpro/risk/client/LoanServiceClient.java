package org.example.risklendpro.risk.client;

import org.example.risklendpro.api.contract.CreditLimitApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Feign 客户端只支持继承单个接口（"Only single inheritance supported"），
 * 贷款域行为/汇总契约拆分到 {@link LoanQueryClient}。
 */
@FeignClient(name = "loan-service", url = "${service.loan.url}")
public interface LoanServiceClient extends CreditLimitApi {
}
