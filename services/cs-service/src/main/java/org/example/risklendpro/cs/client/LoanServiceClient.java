package org.example.risklendpro.cs.client;

import org.example.risklendpro.api.contract.LoanApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * loan-service 内部契约的 Feign 客户端，供 AI 客服实时查询用户授信额度与还款计划。
 * 内部 token 由 common-runtime 的 InternalFeignRequestInterceptor 自动注入。
 * 注意：Feign 只支持单继承，因此 extends 聚合接口 LoanApi（= CreditLimitApi + LoanQueryApi），
 * 不能直接多继承 CreditLimitApi 和 LoanQueryApi。
 */
@FeignClient(name = "loan-service", url = "${service.loan.url}")
public interface LoanServiceClient extends LoanApi {
}
