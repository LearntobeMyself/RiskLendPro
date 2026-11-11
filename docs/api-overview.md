# 后端接口总览（索引）

> 自动扫描自 Controller 源码（路径/方法/权限注解与代码一致）。本表为接口索引，字段级详情见《用户端》/《管理员端》两份接口文档。
> 前端统一走网关 `http://localhost:8088`，**网关调用路径 = 下表「路径」列原样使用**（网关已配置 `/api/v1` 前缀与转发）。

## 一、用户端接口（登录用户调用）

### user-service — 用户服务（8081）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 1 | 贷后监控与额度管理模块 | GET | `/user/credit-limit` | 登录 + 角色 USER | 查看用户额度 |
| 2 | 认证模块 | POST | `/auth/register` | 登录（未限角色） | 用户注册 |
| 3 | 认证模块 | POST | `/auth/login` | 登录（未限角色） | 用户登录 |
### risk-service — 风控服务（8082）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 4 | 授信评估模块 | POST | `/risk/assessment/submit` | 登录 + 角色 USER | 提交风控评估申请 |
| 5 | 授信评估模块 | GET | `/risk/assessment/submit-eligibility` | 登录 + 角色 USER | 可否提交新评估 |
| 6 | 授信评估模块 | GET | `/risk/assessment/status` | 登录 + 角色 USER | 轮询查询评估状态 |
| 7 | 授信评估模块 | GET | `/risk/assessment/result` | 登录 + 角色 USER | 获取最终额度结果 |
| 8 | 补充材料模块 | GET | `/risk/assessment/supplement/requirements` | 登录 + 角色 USER | 查询需补充材料清单 |
| 9 | 补充材料模块 | POST | `/risk/assessment/supplement/upload` | 登录 + 角色 USER | 上传补充材料 |
### loan-service — 借贷服务（8083）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 10 | 借款/支用模块 | POST | `/loan/request` | 登录 + 角色 USER | 发起借款请求 |
| 11 | 借款/支用模块 | GET | `/loan/user/history` | 登录 + 角色 USER | 获取用户借款记录 |
| 12 | 借款/支用模块 | GET | `/loan/user/applications` | 登录 + 角色 USER | 获取用户贷款申请记录 |
| 13 | 借贷产品目录（用户端） | GET | `/loan/products/filters` | 登录 + 角色 USER | U1 获取分类与筛选项 |
| 14 | 借贷产品目录（用户端） | GET | `/loan/products` | 登录 + 角色 USER | U2 产品列表 |
| 15 | 借贷产品目录（用户端） | GET | `/loan/products/compare` | 登录 + 角色 USER | U6 产品对比 |
| 16 | 借贷产品目录（用户端） | GET | `/loan/products/institutions/{institutionId}` | 登录 + 角色 USER | U4 机构详情 |
| 17 | 借贷产品目录（用户端） | GET | `/loan/products/{productId}/similar` | 登录 + 角色 USER | U5 相似产品 |
| 18 | 借贷产品目录（用户端） | GET | `/loan/products/recommendations` | 登录 + 角色 USER | U7 授信通过弹窗推荐 |
| 19 | 借贷产品目录（用户端） | GET | `/loan/products/{productId}/post-loan-recommendations` | 登录 + 角色 USER | U8 成功页再推荐 |
| 20 | 借贷产品目录（用户端） | GET | `/loan/products/{productId}` | 登录 + 角色 USER | U3 产品详情 |
| 21 | 账务与还款模块 | GET | `/repayment/plans` | 登录 + 角色 USER | 查看还款计划 |
| 22 | 账务与还款模块 | POST | `/repayment/execute` | 登录 + 角色 USER | 执行还款 |
| 23 | 账务与还款模块 | GET | `/repayment/record/{planId}` | 登录 + 角色 USER | 获取还款记录详情 |
| 24 | 账务与还款模块 | GET | `/repayment/statistics` | 登录（未限角色） | 获取还款统计 |
| 25 | 账务与还款模块 | GET | `/repayment/overdue` | 登录（未限角色） | 获取逾期统计 |
### cs-service — AI客服服务（8084）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 26 | CsStatusController | GET | `/api/v1/cs/status` | 登录（未限角色） | status |
| 27 | AI 客服（用户端） | POST | `/api/v1/cs/chat` | 登录 + 角色 USER | AI 客服对话 |
| 28 | AI 客服（用户端） | GET | `/api/v1/cs/conversations/{conversationId}/messages` | 登录 + 角色 USER | 会话历史消息 |
| 29 | 转人工工单（用户端） | POST | `/api/v1/cs/tickets` | 登录 + 角色 USER | 提交转人工工单 |
| 30 | 转人工工单（用户端） | GET | `/api/v1/cs/tickets` | 登录 + 角色 USER | 我的工单列表 |

## 二、管理员端接口（管理后台调用）

### user-service — 用户服务（8081）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 1 | 管理员认证模块 | POST | `/admin/register` | 权限点 sys:admin:write | 管理员注册 |
| 2 | 管理员认证模块 | POST | `/admin/login` | 登录（未限角色） | 管理员登录 |
| 3 | 管理员认证模块 | GET | `/admin/profile` | 登录（未限角色） | 当前管理员资料 |
| 4 | 用户管理 | GET | `/admin/users` | 权限点 sys:user | 用户列表 |
| 5 | 用户管理 | GET | `/admin/users/export` | 权限点 sys:user:write | 用户导出 |
| 6 | 用户管理 | GET | `/admin/users/{userId}` | 权限点 sys:user | 用户详情 |
| 7 | 用户管理 | POST | `/admin/users` | 权限点 sys:user:write | 新增用户 |
| 8 | 用户管理 | PUT | `/admin/users/{userId}` | 权限点 sys:user:write | 编辑用户 |
| 9 | 用户管理 | PATCH | `/admin/users/{userId}/status` | 权限点 sys:user:write | 更新用户状态 |
| 10 | 系统管理 | GET | `/admin/system/admins` | 权限点 sys:admin | 管理员列表 |
| 11 | 系统管理 | POST | `/admin/system/admins` | 权限点 sys:admin:write | 新增管理员 |
| 12 | 系统管理 | PUT | `/admin/system/admins/{adminId}` | 权限点 sys:admin:write | 更新管理员 |
| 13 | 系统管理 | DELETE | `/admin/system/admins/{adminId}` | 权限点 sys:admin:write | 删除管理员 |
| 14 | 系统管理 | GET | `/admin/system/operation-logs` | 权限点 audit:read | 操作日志 |
| 15 | 系统管理 | GET | `/admin/system/config` | 权限点 sys:config | 系统配置 |
| 16 | 系统管理 | PUT | `/admin/system/config` | 权限点 sys:config:write | 更新系统配置 |
| 17 | 系统管理 | GET | `/admin/system/backups` | 权限点 sys:config | 备份列表 |
| 18 | 系统管理 | POST | `/admin/system/backups` | 权限点 sys:config:write | 创建备份 |
| 19 | 系统管理 | POST | `/admin/system/backups/{backupId}/restore` | 权限点 sys:config:write | 恢复备份 |
| 20 | 系统管理 | GET | `/admin/system/backups/{backupId}/download` | 权限点 sys:config:write | 下载备份 |
### risk-service — 风控服务（8082）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 21 | 管理员风控审批 | GET | `/admin/risk/list` | 权限点 risk:approve | 获取待审批列表 |
| 22 | 管理员风控审批 | GET | `/admin/risk/report/{applyId}` | 权限点 risk:approve | 获取详细风控报告 |
| 23 | 管理员风控审批 | POST | `/admin/risk/approve` | 权限点 risk:approve:write | 管理员最终审批决策 |
| 24 | 管理员风控审批 | GET | `/admin/b-card/monitor` | 权限点 risk:monitor | B 卡贷后监控列表 |
| 25 | 管理员风控审批 | POST | `/admin/b-card/recalculate/{userId}` | 权限点 risk:monitor:write | 手动重算 B 卡分数 |
| 26 | 管理员风控审批 | POST | `/admin/b-card/snapshot/{userId}` | 权限点 risk:monitor:write | 生成 B 卡 V2 特征快照 |
| 27 | 管理员风控审批 | POST | `/admin/b-card/snapshot-batch` | 权限点 risk:monitor:write | 批量生成 B 卡 V2 特征快照 |
| 28 | 管理员风控审批 | GET | `/admin/b-card/features/{userId}` | 权限点 risk:monitor | 查询最新 B 卡 V2 特征快照 |
| 29 | 风控数据 | GET | `/admin/risk/overview` | 权限点 risk:approve | 风控概览 |
| 30 | 风控数据 | GET | `/admin/risk/credit-scores` | 权限点 risk:approve | 信用评分列表 |
| 31 | 风控数据 | GET | `/admin/risk/users/{userId}` | 权限点 risk:approve | 用户风控详情 |
| 32 | 风控数据 | GET | `/admin/risk/anti-fraud` | 权限点 risk:approve | 反欺诈告警列表 |
| 33 | 风控数据 | POST | `/admin/risk/anti-fraud/{id}/handle` | 权限点 risk:fraud:write | 处理反欺诈告警 |
| 34 | 风控数据 | GET | `/admin/risk/multi-loan` | 权限点 risk:approve | 多头借贷列表 |
| 35 | 风控数据 | GET | `/admin/risk/credit-reports` | 权限点 risk:approve | 征信报告列表 |
| 36 | 风控数据 | GET | `/admin/risk/credit-reports/{userId}` | 权限点 risk:approve | 用户征信报告详情 |
| 37 | 风控数据 | GET | `/admin/risk/export` | 权限点 risk:approve:write | 风控数据导出 |
| 38 | BCardGovernanceController | POST | `/admin/b-card/governance/models` | 登录（未限角色） | register |
| 39 | BCardGovernanceController | POST | `/admin/b-card/governance/models/{version}/approve` | 登录（未限角色） | approve |
| 40 | BCardGovernanceController | POST | `/admin/b-card/governance/models/{version}/activate` | 登录（未限角色） | activate |
| 41 | BCardGovernanceController | POST | `/admin/b-card/governance/models/{version}/rollback` | 登录（未限角色） | rollback |
| 42 | BCardGovernanceController | GET | `/admin/b-card/governance/models` | 登录（未限角色） | listModels |
| 43 | BCardGovernanceController | GET | `/admin/b-card/governance/models/active` | 登录（未限角色） | activeModel |
| 44 | BCardGovernanceController | GET | `/admin/b-card/governance/alerts` | 登录（未限角色） | alerts |
| 45 | BCardGovernanceController | POST | `/admin/b-card/governance/alerts/{id}/ack` | 登录（未限角色） | acknowledge |
| 46 | BCardGovernanceController | POST | `/admin/b-card/governance/alerts/{id}/close` | 登录（未限角色） | close |
| 47 | BCardGovernanceController | POST | `/admin/b-card/governance/monitor/run` | 登录（未限角色） | runMonitor |
| 48 | BCardGovernanceController | POST | `/admin/b-card/governance/fraud/scan` | 登录（未限角色） | scanFraud |
| 49 | BCardGovernanceController | GET | `/admin/b-card/governance/fraud/warnings` | 登录（未限角色） | fraudWarnings |
| 50 | BCardGovernanceController | POST | `/admin/b-card/governance/fraud/warnings/{id}/ack` | 登录（未限角色） | acknowledgeFraud |
| 51 | AdminSupplementController | GET | `/admin/supplement/file/{materialId}` | 权限点 risk:approve | download |
### loan-service — 借贷服务（8083）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 52 | 贷款管理 | GET | `/admin/loan/applications/{loanId}` | 权限点 loan:approve | 贷款申请详情 |
| 53 | 贷款管理 | GET | `/admin/loan/approval-records` | 权限点 loan:approve | 审批记录列表 |
| 54 | 贷款管理 | GET | `/admin/loan/records` | 权限点 loan:approve | 借款记录列表 |
| 55 | 贷款管理 | GET | `/admin/loan/records/{loanId}` | 权限点 loan:approve | 借款记录详情 |
| 56 | 贷款管理 | GET | `/admin/loan/records/export` | 权限点 loan:approve:write | 借款记录导出 |
| 57 | 管理员借款审批与看板 | GET | `/admin/bi/vintage` | 权限点 risk:monitor 或 ROLE_SYS_ADMIN | 获取Vintage曲线数据 |
| 58 | 管理员借款审批与看板 | GET | `/admin/bi/roll-rate` | 权限点 risk:monitor 或 ROLE_SYS_ADMIN | 获取滚动率数据 |
| 59 | 管理员借款审批与看板 | GET | `/admin/dashboard/stats` | 权限点 risk:monitor 或 ROLE_SYS_ADMIN | 获取管理员首页统计 |
| 60 | 管理员借款审批与看板 | GET | `/admin/loan/pending-list` | 权限点 loan:approve | 获取待审批贷款申请列表 |
| 61 | 管理员借款审批与看板 | POST | `/admin/loan/approve` | 权限点 loan:approve:write | 管理员审批贷款申请 |
| 62 | 还款管理 | GET | `/admin/repayment/summary` | 权限点 collection:action | 还款统计汇总 |
| 63 | 还款管理 | GET | `/admin/repayment/plans` | 权限点 collection:action | 还款计划列表 |
| 64 | 还款管理 | GET | `/admin/repayment/plans/{planId}` | 权限点 collection:action | 还款计划详情 |
| 65 | 还款管理 | GET | `/admin/repayment/overdue-stats` | 权限点 collection:case | 逾期统计 |
| 66 | 还款管理 | GET | `/admin/repayment/plans/{planId}/records` | 权限点 collection:action | 计划还款记录列表 |
| 67 | 还款管理 | GET | `/admin/repayment/records/stats` | 权限点 collection:action | 还款记录统计 |
| 68 | 还款管理 | GET | `/admin/repayment/actual-records` | 权限点 collection:action | 实际还款记录 |
| 69 | 还款管理 | GET | `/admin/repayment/overdue-records` | 权限点 collection:case | 逾期记录列表 |
| 70 | 还款管理 | POST | `/admin/repayment/reminders` | 权限点 collection:action:write | 发送还款提醒 |
| 71 | 还款管理 | POST | `/admin/repayment/report` | 权限点 collection:action:write | 生成还款报表 |
| 72 | 借贷产品目录（管理端） | POST | `/admin/products/images/presign` | 权限点 sys:product:write | A1 获取上传凭证 |
| 73 | 借贷产品目录（管理端） | POST | `/admin/products/images/{imageId}/confirm` | 权限点 sys:product:write | A2 确认上传完成 |
| 74 | 借贷产品目录（管理端） | GET | `/admin/products/extra-templates` | 权限点 sys:product | A9 获取补充条件模板 |
| 75 | 借贷产品目录（管理端） | GET | `/admin/products/knowledge/export` | 权限点 sys:product:write | A10 导出产品知识文本 |
| 76 | 借贷产品目录（管理端） | GET | `/admin/products/institutions` | 权限点 sys:product | A11 机构列表 |
| 77 | 借贷产品目录（管理端） | POST | `/admin/products/institutions` | 权限点 sys:product:write | A12 新增机构 |
| 78 | 借贷产品目录（管理端） | PUT | `/admin/products/institutions/{institutionId}` | 权限点 sys:product:write | A13 编辑机构 |
| 79 | 借贷产品目录（管理端） | POST | `/admin/products/institutions/{institutionId}/enabled` | 权限点 sys:product:write | A14 启用/停用机构 |
| 80 | 借贷产品目录（管理端） | GET | `/admin/products/tags` | 权限点 sys:product | A15 标签列表 |
| 81 | 借贷产品目录（管理端） | POST | `/admin/products/tags` | 权限点 sys:product:write | A16 保存标签 |
| 82 | 借贷产品目录（管理端） | DELETE | `/admin/products/tags/{tagId}` | 权限点 sys:product:write | A17 删除标签 |
| 83 | 借贷产品目录（管理端） | GET | `/admin/products` | 权限点 sys:product | A4 管理端产品列表 |
| 84 | 借贷产品目录（管理端） | GET | `/admin/products/{productId}` | 权限点 sys:product | A3 管理端产品详情 |
| 85 | 借贷产品目录（管理端） | POST | `/admin/products` | 权限点 sys:product:write | A5 新增产品 |
| 86 | 借贷产品目录（管理端） | PUT | `/admin/products/{productId}` | 权限点 sys:product:write | A6 编辑产品 |
| 87 | 借贷产品目录（管理端） | POST | `/admin/products/shelf` | 权限点 sys:product:write | A7 上架/下架 |
| 88 | 借贷产品目录（管理端） | DELETE | `/admin/products/{productId}` | 权限点 sys:product:write | A8 删除产品 |
| 89 | 信用额度 | GET | `/admin/credit/stats` | 权限点 credit:adjust | 额度统计 |
| 90 | 信用额度 | GET | `/admin/credit/limits` | 权限点 credit:adjust | 额度列表 |
| 91 | 信用额度 | POST | `/admin/credit/limits/{userId}/adjust` | 权限点 credit:adjust:write | 调整用户额度 |
| 92 | 信用额度 | POST | `/admin/credit/limits/batch-adjust` | 权限点 credit:adjust:write | 批量调整额度 |
| 93 | 信用额度 | GET | `/admin/credit/limits/{userId}/history` | 权限点 credit:adjust | 额度调整历史 |
| 94 | 信用额度 | GET | `/admin/credit/overdue-rules` | 权限点 credit:adjust | 逾期调额规则 |
### cs-service — AI客服服务（8084）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 95 | 人工客服接管（管理端） | GET | `/api/v1/admin/cs/conversations` | 权限点 cs:ticket | 待接入/接待中的会话列表 |
| 96 | 人工客服接管（管理端） | GET | `/api/v1/admin/cs/conversations/{conversationId}/messages` | 权限点 cs:ticket | 会话聊天记录 |
| 97 | 人工客服接管（管理端） | POST | `/api/v1/admin/cs/conversations/{conversationId}/takeover` | 权限点 cs:ticket:write | 接管会话（转人工后由人工接入） |
| 98 | 人工客服接管（管理端） | POST | `/api/v1/admin/cs/conversations/{conversationId}/reply` | 权限点 cs:ticket:write | 人工回复用户 |
| 99 | 人工客服接管（管理端） | POST | `/api/v1/admin/cs/conversations/{conversationId}/close` | 权限点 cs:ticket:write | 结束会话 |
| 100 | 转人工工单（管理端） | GET | `/api/v1/admin/cs/tickets` | 权限点 cs:ticket | 工单列表 |
| 101 | 转人工工单（管理端） | GET | `/api/v1/admin/cs/tickets/{ticketId}` | 权限点 cs:ticket | 工单详情 |
| 102 | 转人工工单（管理端） | POST | `/api/v1/admin/cs/tickets/{ticketId}/process` | 权限点 cs:ticket:write | 领取工单（开始处理） |
| 103 | 转人工工单（管理端） | POST | `/api/v1/admin/cs/tickets/{ticketId}/close` | 权限点 cs:ticket:write | 关闭工单 |

## 三、内部 / Demo 接口（不交付前端，供参考）

### risk-service — 风控服务（8082）

| # | 接口 | 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|---|---|
| 1 | 黑名单同步 | POST | `/sync/blacklist` | 登录（未限角色） | addBlacklist |
| 2 | BehaviorRuleRefreshController | POST | `/internal/behavior-scores/rules/refresh` | 登录（未限角色） | refreshRules |
| 3 | BehaviorRuleRefreshController | POST | `/internal/behavior-scores/rules/info` | 登录（未限角色） | ruleInfo |
| 4 | 图片核验 Demo | POST | `/demo/image-verify` | 登录（未限角色） | 一键核验任意图片 |

## 四、统计

| 分类 | 接口数 |
|---|---|
| 用户端接口 | 30 |
| 管理员端接口 | 103 |
| 内部 / Demo 接口 | 4 |
| **合计** | **137** |
