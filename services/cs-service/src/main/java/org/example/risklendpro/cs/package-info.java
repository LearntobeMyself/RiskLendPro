/**
 * AI 客服与工单域。
 * <p>已实现（阶段 1）：用户端 AI 对话（/api/v1/cs/chat，对接 DeepSeek 大模型，落库 cs_conversation / cs_message），
 * 意图路由区分产品知识 / 实时数据 / 敏感话术 / 转人工，敏感问题走固定话术、大模型失败自动降级。
 * <p>已实现（阶段 2）：转人工工单（cs_ticket），用户建单 + 客服（CS_AGENT）领取/处理/关单，
 * 状态机 PENDING → PROCESSING → CLOSED。
 * <p>已实现（阶段 3）：实时数据 Feign 查询（RealtimeDataService），通过 RiskServiceClient / LoanServiceClient
 * 调 risk/loan 内部契约，回答额度、评估状态、还款计划、补充材料，单源失败独立降级。
 * <p>已实现（阶段 4）：安全收口——SensitiveGuard 敏感拦截（越权/内部规则/隐私数据）、
 * SensitiveDataMasker 脱敏（身份证/手机号/银行卡打码）、CsAuditService 审计日志（敏感拦截/实时查询/工单操作）。
 */
package org.example.risklendpro.cs;
