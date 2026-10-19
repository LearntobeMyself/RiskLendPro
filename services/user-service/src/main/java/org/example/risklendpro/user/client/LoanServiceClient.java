package org.example.risklendpro.user.client;

import org.example.risklendpro.api.contract.LoanApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * user 侧贷款域客户端。
 *
 * <p>必须继承聚合契约 {@link LoanApi}，不能直接写
 * {@code extends CreditLimitApi, LoanQueryApi}：Feign 创建客户端时只支持单接口继承，
 * 多继承会在容器启动阶段抛 "Only single inheritance supported"，
 * 导致整个 user-service 启动失败。</p>
 */
@FeignClient(name = "loan-service", contextId = "loanServiceClient", url = "${service.loan.url}")
public interface LoanServiceClient extends LoanApi {
}
