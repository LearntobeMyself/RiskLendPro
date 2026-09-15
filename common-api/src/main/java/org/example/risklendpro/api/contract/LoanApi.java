package org.example.risklendpro.api.contract;

/**
 * 贷款域聚合契约。由 loan-service 实现，user/risk 通过 Feign 消费。
 * 单独一层继承，规避 Feign "Only single inheritance supported" 限制。
 */
public interface LoanApi extends CreditLimitApi, LoanQueryApi {
}
