# RiskLendPro 后端接口文档 — 管理员端

## 通用说明

### 网关与路径
前端统一走网关 `http://localhost:8088`，请求路径即下文各接口「路径」列原样使用（网关已配置 `/api/v1` 前缀与转发）。

### 统一响应结构
所有接口（特别标注除外）返回统一 JSON：

```json
{ "code": 200, "message": "成功", "success": true, "data": { } }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | number | 状态码，`200` 成功，其他为失败 |
| `message` | string | 提示信息 |
| `success` | boolean | 是否成功 |
| `data` | object/array/标量 | 业务数据，失败时为 `null` |

### 错误码
| code | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数错误 / 业务校验失败 |
| 403 | 无权限访问（HTTP 403） |
| 404 | 接口不存在 |
| 409 | 数据已存在，请勿重复操作 |
| 500 | 服务器内部错误 |

### 鉴权方式
- 登录后，请求头携带 `Authorization: Bearer <JWT>`（登录接口返回 `token`）。
- 各接口所需权限点见「鉴权」列，由后台为管理员角色分配对应权限点。

### 权限点总表
| 权限点 | 说明 |
|---|---|
| `sys:user` / `sys:user:write` | 用户管理：查看 / 新增、编辑、改状态 |
| `sys:admin` / `sys:admin:write` | 管理员管理：查看 / 新增、编辑、删除 |
| `sys:config` / `sys:config:write` | 系统配置：查看 / 修改、备份恢复 |
| `sys:product` / `sys:product:write` | 产品目录：查看 / 新增、编辑、上下架、删除 |
| `audit:read` | 操作日志查看 |
| `risk:approve` / `risk:approve:write` | 风控审批：查看 / 审批、导出 |
| `risk:monitor` / `risk:monitor:write` | B 卡贷后监控：查看 / 重算、生成快照 |
| `risk:fraud:write` | 反欺诈告警处理 |
| `loan:approve` / `loan:approve:write` | 贷款审批：查看 / 审批、导出 |
| `collection:action` / `collection:action:write` / `collection:case` | 还款催收：查看 / 发送提醒、报表 / 逾期管理 |
| `credit:adjust` / `credit:adjust:write` | 信用额度：查看 / 调整额度 |
| `cs:ticket` / `cs:ticket:write` | 客服工单：查看 / 接管、回复、关闭 |
| `ROLE_SYS_ADMIN` | 超级管理员角色（部分看板接口可用此角色替代） |

### 分页结构
- 多数列表接口返回 `{ "list": [ ... ], "total": N }`；
- 部分返回 `{ "records": [ ... ], "total": N, "size": N, "current": N, "pages": N }`。
- 具体以各接口「响应体」说明为准。


## user-service

### 1. 管理员注册

- **方法**：`POST`　**路径**：`/admin/register`
- **鉴权**：需权限点 sys:admin:write
- **所属模块**：管理员认证模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | AdminRegisterRequest | 是 |

**请求体**（`application/json`，类型 `AdminRegisterRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `username` | string | 是 | 昵称 | admin |
| `password` | string | 是 | 密码 | 123456 |
| `rePassword` | string | 是 | 二次密码 | 123456 |
| `phoneNumber` | string | 是 | 手机号 | 13800138001 |
| `email` | string | 否 | 用户邮箱 | admin@example.com |

**响应体**

无 `data` 字段（仅返回 `code`/`message`）。

---

### 2. 管理员登录

- **方法**：`POST`　**路径**：`/admin/login`
- **鉴权**：登录（未限角色）
- **所属模块**：管理员认证模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | AdminLoginRequest | 是 |

**请求体**（`application/json`，类型 `AdminLoginRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `username` | string | 是 | 管理员昵称 | admin |
| `password` | string | 是 | 管理员密码 | 123456 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 管理员ID | — |
| `token` | string | 否 | JWT令牌 | — |
| `role` | string | 否 | 角色码 RISK_MANAGER/COLLECTOR/AUDITOR/CS_AGENT/SYS_ADMIN | — |
| `permissions` | java.util.List<String> | 否 | 权限码列表，供管理端裁菜单 | — |

---

### 3. 当前管理员资料

- **方法**：`GET`　**路径**：`/admin/profile`
- **鉴权**：登录（未限角色）
- **所属模块**：管理员认证模块

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `avatar` | 见联调 |
| `email` | 见联调 |
| `id` | 见联调 |
| `lastLoginTime` | 见联调 |
| `name` | 见联调 |
| `permissions` | 见联调 |
| `phoneNumber` | 见联调 |
| `role` | 见联调 |
| `username` | 见联调 |

---

### 4. 用户列表

- **方法**：`GET`　**路径**：`/admin/users`
- **鉴权**：需权限点 sys:user
- **所属模块**：用户管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `status` | query | string | 否 |
| `accountStatus` | query | string | 否 |
| `name` | query | string | 否 |
| `realName` | query | string | 否 |
| `phone` | query | string | 否 |
| `phoneNumber` | query | string | 否 |

**响应体**

`data` 为分页对象：`{ list: [用户], total }`，`list` 元素字段如下：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | 见联调 | — |
| `userId` | 见联调 | — |
| `name` | 见联调 | — |
| `realName` | 见联调 | — |
| `phone` | 见联调 | — |
| `phoneNumber` | 见联调 | — |
| `idCard` | 见联调 | — |
| `creditScore` | 见联调 | — |
| `creditLimit` | 见联调 | — |
| `loanCount` | 见联调 | — |
| `totalLoan` | 见联调 | — |
| `status` | 见联调 | — |
| `accountStatus` | 见联调 | — |
| `registerTime` | 见联调 | — |
| `createTime` | 见联调 | — |
| `avatar` | 见联调 | — |

---

### 5. 用户导出

- **方法**：`GET`　**路径**：`/admin/users/export`
- **鉴权**：需权限点 sys:user:write
- **所属模块**：用户管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |
| `accountStatus` | query | string | 否 |
| `name` | query | string | 否 |
| `realName` | query | string | 否 |
| `phone` | query | string | 否 |
| `phoneNumber` | query | string | 否 |

**响应体**

`data` 为导出结果对象（或直接返回文件流），字段见 `AdminExportHelper` 实现。

---

### 6. 用户详情

- **方法**：`GET`　**路径**：`/admin/users/{userId}`
- **鉴权**：需权限点 sys:user
- **所属模块**：用户管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `email` | 见联调 |
| `idCard` | 见联调 |
| `lastLoginTime` | 见联调 |

---

### 7. 新增用户

- **方法**：`POST`　**路径**：`/admin/users`
- **鉴权**：需权限点 sys:user:write
- **所属模块**：用户管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | AdminUserCreateRequest | 是 |

**请求体**（`application/json`，类型 `AdminUserCreateRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `name` | string | 否 | 管理员创建用户请求 | — |
| `phone` | string | 否 | 管理员创建用户请求 | — |
| `email` | string | 否 | 管理员创建用户请求 | — |
| `idCard` | string | 否 | 管理员创建用户请求 | — |
| `status` | string | 否 | 管理员创建用户请求 | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `userId` | 见联调 |

---

### 8. 编辑用户

- **方法**：`PUT`　**路径**：`/admin/users/{userId}`
- **鉴权**：需权限点 sys:user:write
- **所属模块**：用户管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |
| `request` | body | AdminUserUpdateRequest | 是 |

**请求体**（`application/json`，类型 `AdminUserUpdateRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `name` | string | 否 | 管理员更新用户请求 | — |
| `phone` | string | 否 | 管理员更新用户请求 | — |
| `email` | string | 否 | 管理员更新用户请求 | — |
| `idCard` | string | 否 | 管理员更新用户请求 | — |
| `status` | string | 否 | 管理员更新用户请求 | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `userId` | 见联调 |

---

### 9. 更新用户状态

- **方法**：`PATCH`　**路径**：`/admin/users/{userId}/status`
- **鉴权**：需权限点 sys:user:write
- **所属模块**：用户管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |
| `request` | body | AdminUserStatusRequest | 是 |

**请求体**（`application/json`，类型 `AdminUserStatusRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `status` | string | 否 | ACTIVE / DISABLED / FROZEN | — |
| `reason` | string | 否 | 操作原因 | — |
| `enabled` | boolean | 否 | 兼容旧字段：true=ACTIVE, false=DISABLED | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `userId` | 见联调 |

---

### 10. 管理员列表

- **方法**：`GET`　**路径**：`/admin/system/admins`
- **鉴权**：需权限点 sys:admin
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `createTime` | 见联调 |
| `email` | 见联调 |
| `id` | 见联调 |
| `phoneNumber` | 见联调 |
| `role` | 见联调 |
| `username` | 见联调 |

---

### 11. 新增管理员

- **方法**：`POST`　**路径**：`/admin/system/admins`
- **鉴权**：需权限点 sys:admin:write
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | AdminCreateRequest | 是 |

**请求体**（`application/json`，类型 `AdminCreateRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `username` | string | 否 | 系统管理员创建请求 | — |
| `password` | string | 否 | 系统管理员创建请求 | — |
| `phoneNumber` | string | 否 | 系统管理员创建请求 | — |
| `email` | string | 否 | 系统管理员创建请求 | — |
| `role` | string | 否 | 角色码 RISK_MANAGER/COLLECTOR/AUDITOR/CS_AGENT/SYS_ADMIN | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `adminId` | 见联调 |

---

### 12. 更新管理员

- **方法**：`PUT`　**路径**：`/admin/system/admins/{adminId}`
- **鉴权**：需权限点 sys:admin:write
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `adminId` | path | number | 是 |
| `request` | body | AdminUpdateRequest | 是 |

**请求体**（`application/json`，类型 `AdminUpdateRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `username` | string | 否 | 系统管理员更新请求 | — |
| `password` | string | 否 | 系统管理员更新请求 | — |
| `phoneNumber` | string | 否 | 系统管理员更新请求 | — |
| `email` | string | 否 | 系统管理员更新请求 | — |
| `role` | string | 否 | 角色码 RISK_MANAGER/COLLECTOR/AUDITOR/CS_AGENT/SYS_ADMIN | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `adminId` | 见联调 |

---

### 13. 删除管理员

- **方法**：`DELETE`　**路径**：`/admin/system/admins/{adminId}`
- **鉴权**：需权限点 sys:admin:write
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `adminId` | path | number | 是 |

**响应体**

无 `data` 字段（仅返回 `code`/`message`）。

---

### 14. 操作日志

- **方法**：`GET`　**路径**：`/admin/system/operation-logs`
- **鉴权**：需权限点 audit:read
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `module` | query | string | 否 |
| `startDate` | query | string | 否 |
| `endDate` | query | string | 否 |

**响应体**

`data` 为分页对象：`{ list: [日志], total }`，`list` 元素字段见 `AdminEntityMapper`（含 `id`、`module`、`action`、`detail`、`createTime` 等）。

---

### 15. 系统配置

- **方法**：`GET`　**路径**：`/admin/system/config`
- **鉴权**：需权限点 sys:config
- **所属模块**：系统管理

**响应体**

`data` 为配置项对象，字段含 `systemName`、`emailEnabled`、`logRetentionDays`、`backupRetentionDays` 等（对应 `SystemConfigUpdateRequest`）。

---

### 16. 更新系统配置

- **方法**：`PUT`　**路径**：`/admin/system/config`
- **鉴权**：需权限点 sys:config:write
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | SystemConfigUpdateRequest | 是 |

**请求体**（`application/json`，类型 `SystemConfigUpdateRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `emailEnabled` | boolean | 否 | 系统配置更新请求 | — |
| `logRetentionDays` | number | 否 | 系统配置更新请求 | — |
| `backupRetentionDays` | number | 否 | 系统配置更新请求 | — |
| `systemName` | string | 否 | 系统配置更新请求 | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `backupRetentionDays` | 见联调 |
| `emailEnabled` | 见联调 |
| `logRetentionDays` | 见联调 |
| `systemName` | 见联调 |

---

### 17. 备份列表

- **方法**：`GET`　**路径**：`/admin/system/backups`
- **鉴权**：需权限点 sys:config
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `createTime` | 见联调 |
| `filename` | 见联调 |
| `id` | 见联调 |
| `size` | 见联调 |
| `status` | 见联调 |

---

### 18. 创建备份

- **方法**：`POST`　**路径**：`/admin/system/backups`
- **鉴权**：需权限点 sys:config:write
- **所属模块**：系统管理

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `backupId` | 见联调 |

---

### 19. 恢复备份

- **方法**：`POST`　**路径**：`/admin/system/backups/{backupId}/restore`
- **鉴权**：需权限点 sys:config:write
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `backupId` | path | string | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `backupId` | 见联调 |

---

### 20. 下载备份

- **方法**：`GET`　**路径**：`/admin/system/backups/{backupId}/download`
- **鉴权**：需权限点 sys:config:write
- **所属模块**：系统管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `backupId` | path | string | 是 |

**响应体**

`data` 为 `ResponseEntity<byte[]>` 类型。

---


## risk-service

### 21. 获取待审批列表

- **方法**：`GET`　**路径**：`/admin/risk/list`
- **鉴权**：需权限点 risk:approve
- **所属模块**：管理员风控审批

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `status` | query | string | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applyId` | 见联调 |
| `applyTime` | 见联调 |
| `auditRemark` | 见联调 |
| `blacklistCheck` | 见联调 |
| `hardReject` | 见联调 |
| `hit` | 见联调 |
| `idCard` | 见联调 |
| `incomeVerification` | 见联调 |
| `level` | 见联调 |
| `needManualReview` | 见联调 |
| `outcomeSummary` | 见联调 |
| `passed` | 见联调 |
| `phone` | 见联调 |
| `reason` | 见联调 |
| `rejectGate` | 见联调 |
| `riskTags` | 见联调 |
| `source` | 见联调 |
| `status` | 见联调 |
| `sysDecision` | 见联调 |
| `totalScore` | 见联调 |
| `userName` | 见联调 |

---

### 22. 获取详细风控报告

- **方法**：`GET`　**路径**：`/admin/risk/report/{applyId}`
- **鉴权**：需权限点 risk:approve
- **所属模块**：管理员风控审批

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `applyId` | path | string | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applyId` | 见联调 |
| `approveFrom` | 见联调 |
| `contribution` | 见联调 |
| `description` | 见联调 |
| `feature` | 见联调 |
| `max` | 见联调 |
| `min` | 见联调 |
| `rejectBelow` | 见联调 |
| `reportCacheHit` | 见联调 |
| `reportCachedAt` | 见联调 |
| `reportDisplay` | 见联调 |
| `scoreDetails` | 见联调 |
| `scoreRecomputed` | 见联调 |
| `scoreThresholds` | 见联调 |
| `scoreZone` | 见联调 |
| `scored` | 见联调 |
| `supplementMaterials` | 见联调 |
| `supplementRequirements` | 见联调 |
| `supplementStatus` | 见联调 |
| `totalScore` | 见联调 |
| `value` | 见联调 |
| `weight` | 见联调 |

---

### 23. 管理员最终审批决策

- **方法**：`POST`　**路径**：`/admin/risk/approve`
- **鉴权**：需权限点 risk:approve:write
- **所属模块**：管理员风控审批

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | RiskApproveRequest | 是 |

**请求体**（`application/json`，类型 `RiskApproveRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `applyId` | string | 否 | 申请ID | L20231027001 |
| `auditResult` | string | 否 | — | — |
| `creditLimit` | number | 否 | 最终额度（通过时必填） | 20000.00 |
| `auditRemark` | string | 否 | 审批评语 | 信用良好，审批通过 |

**响应体**

无 `data` 字段（仅返回 `code`/`message`）。

---

### 24. B 卡贷后监控列表

- **方法**：`GET`　**路径**：`/admin/b-card/monitor`
- **鉴权**：需权限点 risk:monitor
- **所属模块**：管理员风控审批

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `activePlanCount` | 见联调 |
| `bScore` | 见联调 |
| `bScoreUpdatedAt` | 见联调 |
| `baseScore` | 见联调 |
| `baseScoreResolved` | 见联调 |
| `currentPeriod` | 见联调 |
| `dataStatus` | 见联调 |
| `daysToDue` | 见联调 |
| `deltaScore` | 见联调 |
| `dueDate` | 见联调 |
| `hasOverdue` | 见联调 |
| `idCard` | 见联调 |
| `limitMultiplier` | 见联调 |
| `liveFeatures` | 见联调 |
| `overdueDays` | 见联调 |
| `overdueLevel` | 见联调 |
| `phone` | 见联调 |
| `planId` | 见联调 |
| `planStatus` | 见联调 |
| `recordStatus` | 见联调 |
| `scoreReliable` | 见联调 |
| `totalLimit` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |
| `watchLevel` | 见联调 |

---

### 25. 手动重算 B 卡分数

- **方法**：`POST`　**路径**：`/admin/b-card/recalculate/{userId}`
- **鉴权**：需权限点 risk:monitor:write
- **所属模块**：管理员风控审批

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `bScore` | 见联调 |
| `bScoreUpdatedAt` | 见联调 |
| `baseScore` | 见联调 |
| `baseScoreResolved` | 见联调 |
| `dataStatus` | 见联调 |
| `deltaScore` | 见联调 |
| `limitMultiplier` | 见联调 |
| `liveFeatures` | 见联调 |
| `raw` | 见联调 |
| `scoreReliable` | 见联调 |
| `userId` | 见联调 |

---

### 26. 生成 B 卡 V2 特征快照

- **方法**：`POST`　**路径**：`/admin/b-card/snapshot/{userId}`
- **鉴权**：需权限点 risk:monitor:write
- **所属模块**：管理员风控审批

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 27. 批量生成 B 卡 V2 特征快照

- **方法**：`POST`　**路径**：`/admin/b-card/snapshot-batch`
- **鉴权**：需权限点 risk:monitor:write
- **所属模块**：管理员风控审批

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 28. 查询最新 B 卡 V2 特征快照

- **方法**：`GET`　**路径**：`/admin/b-card/features/{userId}`
- **鉴权**：需权限点 risk:monitor
- **所属模块**：管理员风控审批

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 29. 风控概览

- **方法**：`GET`　**路径**：`/admin/risk/overview`
- **鉴权**：需权限点 risk:approve
- **所属模块**：风控数据

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `aScoreDistribution` | 见联调 |
| `bScoreDistribution` | 见联调 |
| `level` | 见联调 |
| `totalUsers` | 见联调 |

---

### 30. 信用评分列表

- **方法**：`GET`　**路径**：`/admin/risk/credit-scores`
- **鉴权**：需权限点 risk:approve
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `userName` | query | string | 否 |
| `riskLevel` | query | string | 否 |

**响应体**

`data` 为分页对象：`{ list: [评分项], total }`。

---

### 31. 用户风控详情

- **方法**：`GET`　**路径**：`/admin/risk/users/{userId}`
- **鉴权**：需权限点 risk:approve
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `activeLoansCount` | 见联调 |
| `amtIncomeTotal` | 见联调 |
| `creditBureauMon` | 见联调 |
| `externalFeatures` | 见联调 |
| `externalFeaturesDb` | 见联调 |
| `hasRiskAssessment` | 见联调 |
| `prevRefusedCount` | 见联调 |
| `reportDisplay` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 32. 反欺诈告警列表

- **方法**：`GET`　**路径**：`/admin/risk/anti-fraud`
- **鉴权**：需权限点 risk:approve
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `status` | query | string | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `alertType` | 见联调 |
| `createTime` | 见联调 |
| `description` | 见联调 |
| `id` | 见联调 |
| `riskLevel` | 见联调 |
| `status` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 33. 处理反欺诈告警

- **方法**：`POST`　**路径**：`/admin/risk/anti-fraud/{id}/handle`
- **鉴权**：需权限点 risk:fraud:write
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `id` | path | string | 是 |
| `request` | body | AntiFraudHandleRequest | 是 |

**请求体**（`application/json`，类型 `AntiFraudHandleRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `action` | string | 否 | 处理动作：CONFIRM / DISMISS / ESCALATE | — |
| `remark` | string | 否 | 处理备注 | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `handleTime` | 见联调 |
| `handlerId` | 见联调 |
| `id` | 见联调 |
| `remark` | 见联调 |
| `status` | 见联调 |

---

### 34. 多头借贷列表

- **方法**：`GET`　**路径**：`/admin/risk/multi-loan`
- **鉴权**：需权限点 risk:approve
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `minActiveLoans` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `activeLoansCount` | 见联调 |
| `creditBureauMon` | 见联调 |
| `idCard` | 见联调 |
| `riskLevel` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 35. 征信报告列表

- **方法**：`GET`　**路径**：`/admin/risk/credit-reports`
- **鉴权**：需权限点 risk:approve
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `status` | query | string | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applyId` | 见联调 |
| `status` | 见联调 |
| `submitTime` | 见联调 |
| `sysDecision` | 见联调 |
| `totalScore` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 36. 用户征信报告详情

- **方法**：`GET`　**路径**：`/admin/risk/credit-reports/{userId}`
- **鉴权**：需权限点 risk:approve
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 37. 风控数据导出

- **方法**：`GET`　**路径**：`/admin/risk/export`
- **鉴权**：需权限点 risk:approve:write
- **所属模块**：风控数据

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applyId` | 见联调 |
| `creditLimit` | 见联调 |
| `riskLevel` | 见联调 |
| `status` | 见联调 |
| `submitTime` | 见联调 |
| `totalScore` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 38. register

- **方法**：`POST`　**路径**：`/admin/b-card/governance/models`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | RegisterModelRequest | 是 |

**请求体**（`application/json`，类型 `RegisterModelRequest`）

（字段结构见 `RegisterModelRequest` 类定义）

**响应体**

`data` 为 `BCardModelRegistryStore.ModelRecord` 类型。

---

### 39. approve

- **方法**：`POST`　**路径**：`/admin/b-card/governance/models/{version}/approve`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `actor` | query | string | 是 |
| `version` | path | string | 是 |

**响应体**

`data` 为 `BCardModelRegistryStore.ModelRecord` 类型。

---

### 40. activate

- **方法**：`POST`　**路径**：`/admin/b-card/governance/models/{version}/activate`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `actor` | query | string | 是 |
| `version` | path | string | 是 |

**响应体**

`data` 为 `BCardModelRegistryStore.ModelRecord` 类型。

---

### 41. rollback

- **方法**：`POST`　**路径**：`/admin/b-card/governance/models/{version}/rollback`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `actor` | query | string | 是 |
| `version` | path | string | 是 |

**响应体**

`data` 为 `BCardModelRegistryStore.ModelRecord` 类型。

---

### 42. listModels

- **方法**：`GET`　**路径**：`/admin/b-card/governance/models`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**响应体**

`data` 为数组，元素类型 `BCardModelRegistryStore.ModelRecord`。

---

### 43. activeModel

- **方法**：`GET`　**路径**：`/admin/b-card/governance/models/active`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**响应体**

`data` 为 `BCardModelRegistryStore.ModelRecord` 类型。

---

### 44. alerts

- **方法**：`GET`　**路径**：`/admin/b-card/governance/alerts`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |

**响应体**

`data` 为数组，元素类型 `object`。

---

### 45. acknowledge

- **方法**：`POST`　**路径**：`/admin/b-card/governance/alerts/{id}/ack`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `actor` | query | string | 是 |
| `id` | path | number | 是 |

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 46. close

- **方法**：`POST`　**路径**：`/admin/b-card/governance/alerts/{id}/close`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `actor` | query | string | 是 |
| `id` | path | number | 是 |

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 47. runMonitor

- **方法**：`POST`　**路径**：`/admin/b-card/governance/monitor/run`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**响应体**

`data` 为数组，元素类型 `BCardMonitorMetrics.AlertDecision`。

---

### 48. scanFraud

- **方法**：`POST`　**路径**：`/admin/b-card/governance/fraud/scan`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `limit` | query | number | 是 |

**响应体**

`data` 为数组，元素类型 `object`。

---

### 49. fraudWarnings

- **方法**：`GET`　**路径**：`/admin/b-card/governance/fraud/warnings`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**响应体**

`data` 为数组，元素类型 `object`。

---

### 50. acknowledgeFraud

- **方法**：`POST`　**路径**：`/admin/b-card/governance/fraud/warnings/{id}/ack`
- **鉴权**：登录（未限角色）
- **所属模块**：BCardGovernanceController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `actor` | query | string | 是 |
| `id` | path | number | 是 |

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 51. download

- **方法**：`GET`　**路径**：`/admin/supplement/file/{materialId}`
- **鉴权**：需权限点 risk:approve
- **所属模块**：AdminSupplementController

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `inline` | query | boolean | 是 |
| `materialId` | path | number | 是 |

**响应体**

`data` 为 `ResponseEntity<Resource>` 类型。

---


## loan-service

### 52. 贷款申请详情

- **方法**：`GET`　**路径**：`/admin/loan/applications/{loanId}`
- **鉴权**：需权限点 loan:approve
- **所属模块**：贷款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `loanId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `amount` | 见联调 |
| `applyTime` | 见联调 |
| `creditScore` | 见联调 |
| `currentLimit` | 见联调 |
| `exceedAmount` | 见联调 |
| `idCard` | 见联调 |
| `interestRate` | 见联调 |
| `loanId` | 见联调 |
| `materials` | 见联调 |
| `phone` | 见联调 |
| `purpose` | 见联调 |
| `remainingLimit` | 见联调 |
| `repaymentMethod` | 见联调 |
| `riskReportId` | 见联调 |
| `status` | 见联调 |
| `termMonths` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 53. 审批记录列表

- **方法**：`GET`　**路径**：`/admin/loan/approval-records`
- **鉴权**：需权限点 loan:approve
- **所属模块**：贷款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `userId` | query | number | 否 |
| `loanId` | query | number | 否 |
| `status` | query | string | 否 |
| `startDate` | query | string | 否 |
| `endDate` | query | string | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applicantName` | 见联调 |
| `comment` | 见联调 |
| `createTime` | 见联调 |
| `id` | 见联调 |
| `loanId` | 见联调 |
| `reviewerId` | 见联调 |
| `reviewerName` | 见联调 |
| `status` | 见联调 |
| `updateTime` | 见联调 |
| `userId` | 见联调 |

---

### 54. 借款记录列表

- **方法**：`GET`　**路径**：`/admin/loan/records`
- **鉴权**：需权限点 loan:approve
- **所属模块**：贷款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `status` | query | string | 否 |
| `userName` | query | string | 否 |
| `startDate` | query | string | 否 |
| `endDate` | query | string | 否 |

**响应体**

`data` 为分页对象：`{ list: [借款记录], total }`。

---

### 55. 借款记录详情

- **方法**：`GET`　**路径**：`/admin/loan/records/{loanId}`
- **鉴权**：需权限点 loan:approve
- **所属模块**：贷款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `loanId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `idCard` | 见联调 |
| `paidAmount` | 见联调 |
| `remainingAmount` | 见联调 |
| `repaymentMethod` | 见联调 |

---

### 56. 借款记录导出

- **方法**：`GET`　**路径**：`/admin/loan/records/export`
- **鉴权**：需权限点 loan:approve:write
- **所属模块**：贷款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |
| `userName` | query | string | 否 |
| `startDate` | query | string | 否 |
| `endDate` | query | string | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `avatar` | 见联调 |
| `dueTime` | 见联调 |
| `interestRate` | 见联调 |
| `loanAmount` | 见联调 |
| `loanId` | 见联调 |
| `loanTerm` | 见联调 |
| `loanTime` | 见联调 |
| `overdueDays` | 见联调 |
| `paidAmount` | 见联调 |
| `phone` | 见联调 |
| `status` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 57. 获取Vintage曲线数据

- **方法**：`GET`　**路径**：`/admin/bi/vintage`
- **鉴权**：需权限点 risk:monitor 或 ROLE_SYS_ADMIN
- **所属模块**：管理员借款审批与看板

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `M1Rate` | 见联调 |
| `M2Rate` | 见联调 |
| `M3Rate` | 见联调 |
| `disbursedAmount` | 见联调 |
| `month` | 见联调 |
| `months` | 见联调 |
| `vintageData` | 见联调 |

---

### 58. 获取滚动率数据

- **方法**：`GET`　**路径**：`/admin/bi/roll-rate`
- **鉴权**：需权限点 risk:monitor 或 ROLE_SYS_ADMIN
- **所属模块**：管理员借款审批与看板

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `currentStatus` | 见联调 |
| `nextMonthStatus` | 见联调 |

---

### 59. 获取管理员首页统计

- **方法**：`GET`　**路径**：`/admin/dashboard/stats`
- **鉴权**：需权限点 risk:monitor 或 ROLE_SYS_ADMIN
- **所属模块**：管理员借款审批与看板

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `approvedToday` | 见联调 |
| `overdueRate` | 见联调 |
| `pendingReview` | 见联调 |
| `rejectedToday` | 见联调 |
| `totalApplications` | 见联调 |
| `totalDisbursed` | 见联调 |
| `totalOverdue` | 见联调 |

---

### 60. 获取待审批贷款申请列表

- **方法**：`GET`　**路径**：`/admin/loan/pending-list`
- **鉴权**：需权限点 loan:approve
- **所属模块**：管理员借款审批与看板

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `amount` | 见联调 |
| `applyTime` | 见联调 |
| `collateralType` | 见联调 |
| `collateralValue` | 见联调 |
| `currentLimit` | 见联调 |
| `exceedAmount` | 见联调 |
| `idCard` | 见联调 |
| `loanId` | 见联调 |
| `phone` | 见联调 |
| `productId` | 见联调 |
| `productName` | 见联调 |
| `remainingLimit` | 见联调 |
| `repaymentMethod` | 见联调 |
| `status` | 见联调 |
| `termMonths` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 61. 管理员审批贷款申请

- **方法**：`POST`　**路径**：`/admin/loan/approve`
- **鉴权**：需权限点 loan:approve:write
- **所属模块**：管理员借款审批与看板

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | LoanApproveRequest | 是 |

**请求体**（`application/json`，类型 `LoanApproveRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `loanId` | number | 否 | 贷款申请ID | 1 |
| `approveResult` | string | 否 | — | — |
| `collateralValue` | number | 否 | 抵押物评估价（房抵/车抵审批通过时必填，管理员确认抵押物时录入） | 300000.00 |
| `collateralType` | string | 否 | 抵押物类型 HOUSE/VEHICLE，缺省时取借据上已记录的类型 | HOUSE |
| `approveRemark` | string | 否 | 审批评语 | 借款金额合理，审批通过 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `loanId` | number | 否 | 贷款ID | — |
| `userId` | number | 否 | 用户ID | — |
| `actualDisbursedAmount` | number | 否 | 实际发放金额 | — |
| `status` | string | 否 | 状态 | — |
| `approveTime` | string(时间) | 否 | 审批时间 | — |
| `rejectReason` | string | 否 | 拒绝原因 | — |
| `emailSent` | boolean | 否 | 邮件是否发送 | — |

---

### 62. 还款统计汇总

- **方法**：`GET`　**路径**：`/admin/repayment/summary`
- **鉴权**：需权限点 collection:action
- **所属模块**：还款管理

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `overdueAmount` | 见联调 |
| `pendingAmount` | 见联调 |
| `receivableThisMonth` | 见联调 |
| `receivedThisMonth` | 见联调 |

---

### 63. 还款计划列表

- **方法**：`GET`　**路径**：`/admin/repayment/plans`
- **鉴权**：需权限点 collection:action
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `loanId` | query | number | 否 |
| `userName` | query | string | 否 |
| `status` | query | string | 否 |

**响应体**

`data` 为分页对象：`{ list: [还款计划], total }`。

---

### 64. 还款计划详情

- **方法**：`GET`　**路径**：`/admin/repayment/plans/{planId}`
- **鉴权**：需权限点 collection:action
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `planId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `activeRecords` | 见联调 |
| `completedRecords` | 见联调 |
| `overdueRecords` | 见联调 |
| `repaymentRecords` | 见联调 |

---

### 65. 逾期统计

- **方法**：`GET`　**路径**：`/admin/repayment/overdue-stats`
- **鉴权**：需权限点 collection:case
- **所属模块**：还款管理

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `totalCount` | 见联调 |

---

### 66. 计划还款记录列表

- **方法**：`GET`　**路径**：`/admin/repayment/plans/{planId}/records`
- **鉴权**：需权限点 collection:action
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `status` | query | string | 否 |
| `planId` | path | number | 是 |

**响应体**

`data` 为分页对象：`{ list: [还款记录], total }`。

---

### 67. 还款记录统计

- **方法**：`GET`　**路径**：`/admin/repayment/records/stats`
- **鉴权**：需权限点 collection:action
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `planId` | query | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `totalCount` | 见联调 |

---

### 68. 实际还款记录

- **方法**：`GET`　**路径**：`/admin/repayment/actual-records`
- **鉴权**：需权限点 collection:action
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `loanId` | query | number | 否 |
| `userName` | query | string | 否 |
| `startDate` | query | string | 否 |
| `endDate` | query | string | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `id` | 见联调 |
| `loanId` | 见联调 |
| `operator` | 见联调 |
| `repayAmount` | 见联调 |
| `repayDate` | 见联调 |
| `repayType` | 见联调 |
| `status` | 见联调 |
| `userName` | 见联调 |

---

### 69. 逾期记录列表

- **方法**：`GET`　**路径**：`/admin/repayment/overdue-records`
- **鉴权**：需权限点 collection:case
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `contactTimes` | 见联调 |
| `id` | 见联调 |
| `lastContact` | 见联调 |
| `loanId` | 见联调 |
| `overdueAmount` | 见联调 |
| `overdueDate` | 见联调 |
| `overdueDays` | 见联调 |
| `status` | 见联调 |
| `userName` | 见联调 |

---

### 70. 发送还款提醒

- **方法**：`POST`　**路径**：`/admin/repayment/reminders`
- **鉴权**：需权限点 collection:action:write
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | RepaymentReminderRequest | 是 |

**请求体**（`application/json`，类型 `RepaymentReminderRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `userId` | number | 否 | 还款提醒请求 | — |
| `loanId` | number | 否 | 还款提醒请求 | — |
| `planId` | number | 否 | 还款提醒请求 | — |
| `channel` | string | 否 | 还款提醒请求 | — |
| `message` | string | 否 | 还款提醒请求 | — |

**响应体**

`data` 为 JSON 对象（字段由后端动态组装，见代码实现）。

---

### 71. 生成还款报表

- **方法**：`POST`　**路径**：`/admin/repayment/report`
- **鉴权**：需权限点 collection:action:write
- **所属模块**：还款管理

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | RepaymentReportRequest | 是 |

**请求体**（`application/json`，类型 `RepaymentReportRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `startDate` | string | 否 | 还款报表生成请求 | — |
| `endDate` | string | 否 | 还款报表生成请求 | — |
| `type` | string | 否 | 还款报表生成请求 | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `actualAmount` | 见联调 |
| `amount` | 见联调 |
| `createTime` | 见联调 |
| `currentPeriod` | 见联调 |
| `dueDate` | 见联调 |
| `endDate` | 见联调 |
| `interest` | 见联调 |
| `interestAmount` | 见联调 |
| `loanId` | 见联调 |
| `overdueDays` | 见联调 |
| `paidAmount` | 见联调 |
| `paidPeriods` | 见联调 |
| `period` | 见联调 |
| `planId` | 见联调 |
| `principal` | 见联调 |
| `principalAmount` | 见联调 |
| `progressPercentage` | 见联调 |
| `recordId` | 见联调 |
| `remainingAmount` | 见联调 |
| `repaymentDate` | 见联调 |
| `repaymentMethod` | 见联调 |
| `startDate` | 见联调 |
| `status` | 见联调 |
| `statusDesc` | 见联调 |
| `totalAmount` | 见联调 |
| `totalPeriods` | 见联调 |
| `updateTime` | 见联调 |
| `userId` | 见联调 |
| `userName` | 见联调 |

---

### 72. A1 获取上传凭证

- **方法**：`POST`　**路径**：`/admin/products/images/presign`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | ImagePresignRequest | 是 |

**请求体**（`application/json`，类型 `ImagePresignRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `imageType` | string | 否 | — | — |
| `fileName` | string | 否 | — | — |
| `contentType` | string | 否 | — | — |
| `fileSize` | number | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `expireAt` | 见联调 |
| `headers` | 见联调 |
| `imageId` | 见联调 |
| `method` | 见联调 |
| `objectKey` | 见联调 |
| `uploadUrl` | 见联调 |

---

### 73. A2 确认上传完成

- **方法**：`POST`　**路径**：`/admin/products/images/{imageId}/confirm`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `imageId` | path | number | 是 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `imageId` | number | 否 | — | — |
| `imageType` | string | 否 | — | — |
| `url` | string | 否 | — | — |
| `sort` | number | 否 | — | — |
| `objectKey` | string | 否 | — | — |

---

### 74. A9 获取补充条件模板

- **方法**：`GET`　**路径**：`/admin/products/extra-templates`
- **鉴权**：需权限点 sys:product
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `categoryCode` | query | string | 是 |

**响应体**

`data` 为数组，元素类型 `object`。

---

### 75. A10 导出产品知识文本

- **方法**：`GET`　**路径**：`/admin/products/knowledge/export`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `productIds` | query | array<number> | 是 |
| `format` | query | string | 否 |

**响应体**

`data` 为数组，元素类型 `object`。

---

### 76. A11 机构列表

- **方法**：`GET`　**路径**：`/admin/products/institutions`
- **鉴权**：需权限点 sys:product
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `keyword` | query | string | 是 |
| `institutionType` | query | string | 否 |
| `enabled` | query | number | 否 |
| `pageNum` | query | number | 否 |
| `pageSize` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `enabled` | 见联调 |
| `introduction` | 见联调 |
| `onShelfCount` | 见联调 |
| `productCount` | 见联调 |
| `version` | 见联调 |

---

### 77. A12 新增机构

- **方法**：`POST`　**路径**：`/admin/products/institutions`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | InstitutionSaveRequest | 是 |

**请求体**（`application/json`，类型 `InstitutionSaveRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `institutionName` | string | 否 | — | — |
| `institutionType` | string | 否 | — | — |
| `logoImageId` | number | 否 | — | — |
| `introduction` | string | 否 | — | — |
| `version` | number | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `institutionId` | 见联调 |
| `name` | 见联调 |

---

### 78. A13 编辑机构

- **方法**：`PUT`　**路径**：`/admin/products/institutions/{institutionId}`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `institutionId` | path | number | 是 |
| `request` | body | InstitutionSaveRequest | 是 |

**请求体**（`application/json`，类型 `InstitutionSaveRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `institutionName` | string | 否 | — | — |
| `institutionType` | string | 否 | — | — |
| `logoImageId` | number | 否 | — | — |
| `introduction` | string | 否 | — | — |
| `version` | number | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `version` | 见联调 |

---

### 79. A14 启用/停用机构

- **方法**：`POST`　**路径**：`/admin/products/institutions/{institutionId}/enabled`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `enabled` | query | boolean | 是 |
| `institutionId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `autoOffShelfProductIds` | 见联调 |
| `institutionId` | 见联调 |

---

### 80. A15 标签列表

- **方法**：`GET`　**路径**：`/admin/products/tags`
- **鉴权**：需权限点 sys:product
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `categoryCode` | query | string | 是 |

**响应体**

`data` 为数组，元素字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `tagId` | number | 否 | — | — |
| `tagName` | string | 否 | — | — |
| `categoryCode` | string | 否 | — | — |
| `productCount` | number | 否 | — | — |

---

### 81. A16 保存标签

- **方法**：`POST`　**路径**：`/admin/products/tags`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | TagSaveRequest | 是 |

**请求体**（`application/json`，类型 `TagSaveRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `tagId` | number | 否 | — | — |
| `tagName` | string | 否 | — | — |
| `categoryCode` | string | 否 | — | — |
| `sort` | number | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `tagId` | 见联调 |
| `tagName` | 见联调 |

---

### 82. A17 删除标签

- **方法**：`DELETE`　**路径**：`/admin/products/tags/{tagId}`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `tagId` | path | number | 是 |

**响应体**

无 `data` 字段（仅返回 `code`/`message`）。

---

### 83. A4 管理端产品列表

- **方法**：`GET`　**路径**：`/admin/products`
- **鉴权**：需权限点 sys:product
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `keyword` | query | string | 是 |
| `categoryCode` | query | string | 否 |
| `institutionId` | query | number | 否 |
| `status` | query | string | 否 |
| `pageNum` | query | number | 否 |
| `pageSize` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `completeness` | 见联调 |
| `status` | 见联调 |
| `updateBy` | 见联调 |
| `updateTime` | 见联调 |
| `verifiedAt` | 见联调 |

---

### 84. A3 管理端产品详情

- **方法**：`GET`　**路径**：`/admin/products/{productId}`
- **鉴权**：需权限点 sys:product
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `productId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `tagIds` | 见联调 |

---

### 85. A5 新增产品

- **方法**：`POST`　**路径**：`/admin/products`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | ProductSaveRequest | 是 |

**请求体**（`application/json`，类型 `ProductSaveRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `productName` | string | 否 | — | — |
| `institutionId` | number | 否 | — | — |
| `categoryCode` | string | 否 | — | — |
| `tagIds` | array<number> | 否 | — | — |
| `summary` | string | 否 | — | — |
| `coverImageId` | number | 否 | — | — |
| `detailImageIds` | array<number> | 否 | — | — |
| `loanInfo` | LoanInfo | 否 | — | — |
| `conditions` | Conditions | 否 | — | — |
| `materials` | array<string> | 否 | — | — |
| `others` | Others | 否 | — | — |
| `dataSource` | string | 否 | — | — |
| `verifiedAt` | string(时间) | 否 | — | — |
| `sortWeight` | number | 否 | — | — |
| `version` | number | 否 | — | — |
| `minAmount` | number | 否 | — | — |
| `maxAmount` | number | 否 | — | — |
| `minTerm` | number | 否 | — | — |
| `maxTerm` | number | 否 | — | — |
| `termUnit` | string | 否 | — | — |
| `minAnnualRate` | number | 否 | — | — |
| `maxAnnualRate` | number | 否 | — | — |
| `rateCalcMethod` | string | 否 | — | — |
| `rateScope` | string | 否 | — | — |
| `repaymentMethods` | array<string> | 否 | — | — |
| `disbursementTime` | string | 否 | — | — |
| `targetGroups` | array<string> | 否 | — | — |
| `minAge` | number | 否 | — | — |
| `maxAge` | number | 否 | — | — |
| `incomeRequirement` | string | 否 | — | — |
| `creditRequirement` | string | 否 | — | — |
| `occupationRequirement` | string | 否 | — | — |
| `mortgageRequired` | string | 否 | — | — |
| `regions` | array<string> | 否 | — | — |
| `extraConditions` | object | 否 | — | — |
| `feeStatus` | string | 否 | — | — |
| `feeDescription` | string | 否 | — | — |
| `prepaymentDescription` | string | 否 | — | — |
| `remark` | string | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `productId` | 见联调 |
| `productName` | 见联调 |

---

### 86. A6 编辑产品

- **方法**：`PUT`　**路径**：`/admin/products/{productId}`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `productId` | path | number | 是 |
| `request` | body | ProductSaveRequest | 是 |

**请求体**（`application/json`，类型 `ProductSaveRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `productName` | string | 否 | — | — |
| `institutionId` | number | 否 | — | — |
| `categoryCode` | string | 否 | — | — |
| `tagIds` | array<number> | 否 | — | — |
| `summary` | string | 否 | — | — |
| `coverImageId` | number | 否 | — | — |
| `detailImageIds` | array<number> | 否 | — | — |
| `loanInfo` | LoanInfo | 否 | — | — |
| `conditions` | Conditions | 否 | — | — |
| `materials` | array<string> | 否 | — | — |
| `others` | Others | 否 | — | — |
| `dataSource` | string | 否 | — | — |
| `verifiedAt` | string(时间) | 否 | — | — |
| `sortWeight` | number | 否 | — | — |
| `version` | number | 否 | — | — |
| `minAmount` | number | 否 | — | — |
| `maxAmount` | number | 否 | — | — |
| `minTerm` | number | 否 | — | — |
| `maxTerm` | number | 否 | — | — |
| `termUnit` | string | 否 | — | — |
| `minAnnualRate` | number | 否 | — | — |
| `maxAnnualRate` | number | 否 | — | — |
| `rateCalcMethod` | string | 否 | — | — |
| `rateScope` | string | 否 | — | — |
| `repaymentMethods` | array<string> | 否 | — | — |
| `disbursementTime` | string | 否 | — | — |
| `targetGroups` | array<string> | 否 | — | — |
| `minAge` | number | 否 | — | — |
| `maxAge` | number | 否 | — | — |
| `incomeRequirement` | string | 否 | — | — |
| `creditRequirement` | string | 否 | — | — |
| `occupationRequirement` | string | 否 | — | — |
| `mortgageRequired` | string | 否 | — | — |
| `regions` | array<string> | 否 | — | — |
| `extraConditions` | object | 否 | — | — |
| `feeStatus` | string | 否 | — | — |
| `feeDescription` | string | 否 | — | — |
| `prepaymentDescription` | string | 否 | — | — |
| `remark` | string | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `productName` | 见联调 |
| `version` | 见联调 |

---

### 87. A7 上架/下架

- **方法**：`POST`　**路径**：`/admin/products/shelf`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | ShelfRequest | 是 |

**请求体**（`application/json`，类型 `ShelfRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `productIds` | array<number> | 否 | — | — |
| `targetStatus` | string | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `productId` | 见联调 |
| `successIds` | 见联调 |

---

### 88. A8 删除产品

- **方法**：`DELETE`　**路径**：`/admin/products/{productId}`
- **鉴权**：需权限点 sys:product:write
- **所属模块**：借贷产品目录（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `productId` | path | number | 是 |

**响应体**

无 `data` 字段（仅返回 `code`/`message`）。

---

### 89. 获客推荐漏斗指标

- **方法**：`GET`　**路径**：`/admin/products/recommendation/metrics`
- **鉴权**：需权限点 sys:product
- **所属模块**：获客推荐（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `scene` | query | string | 是 |
| `days` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applies` | 见联调 |
| `applyConversionRate` | 见联调 |
| `clickThroughRate` | 见联调 |
| `clicks` | 见联调 |
| `ctr` | 见联调 |
| `cvr` | 见联调 |
| `days` | 见联调 |
| `detailViews` | 见联调 |
| `impressions` | 见联调 |
| `productId` | 见联调 |
| `scene` | 见联调 |
| `topProducts` | 见联调 |
| `uniqueUsers` | 见联调 |

---

### 90. 获取推荐场景配置

- **方法**：`GET`　**路径**：`/admin/products/recommendation/config`
- **鉴权**：需权限点 sys:product
- **所属模块**：获客推荐（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `scene` | query | string | 是 |

**响应体**

`data` 对象：`{ weights: { ... }, candidateLimit, explorationRate, maxPerInstitution, maxPerCategory, fatigueDays, fatiguePenalty }` 等推荐参数。

---

### 91. 保存推荐场景配置

- **方法**：`PUT`　**路径**：`/admin/products/recommendation/config`
- **鉴权**：需权限点 sys:product
- **所属模块**：获客推荐（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `scene` | query | string | 是 |
| `request` | body | RecommendationConfigRequest | 是 |

**请求体**（`application/json`，类型 `RecommendationConfigRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `config` | object | 否 | — | — |
| `enabled` | boolean | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `candidateLimit` | 见联调 |
| `explorationRate` | 见联调 |
| `fatigueDays` | 见联调 |
| `fatiguePenalty` | 见联调 |
| `maxPerCategory` | 见联调 |
| `maxPerInstitution` | 见联调 |
| `weights` | 见联调 |

---

### 92. 额度统计

- **方法**：`GET`　**路径**：`/admin/credit/stats`
- **鉴权**：需权限点 credit:adjust
- **所属模块**：信用额度

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `availableCreditLimit` | 见联调 |
| `averageUsageRate` | 见联调 |
| `totalCreditLimit` | 见联调 |
| `trends` | 见联调 |
| `usedCreditLimit` | 见联调 |
| `userCount` | 见联调 |

---

### 93. 额度列表

- **方法**：`GET`　**路径**：`/admin/credit/limits`
- **鉴权**：需权限点 credit:adjust
- **所属模块**：信用额度

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `userName` | query | string | 否 |
| `phone` | query | string | 否 |
| `hasOverdue` | query | boolean | 否 |
| `status` | query | string | 否 |

**响应体**

`data` 为分页对象：`{ list: [额度项], total }`，额度项字段含 `userId`、`totalLimit`、`usedLimit`、`remainingLimit` 等。

---

### 94. 调整用户额度

- **方法**：`POST`　**路径**：`/admin/credit/limits/{userId}/adjust`
- **鉴权**：需权限点 credit:adjust:write
- **所属模块**：信用额度

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `userId` | path | number | 是 |
| `request` | body | LimitAdjustRequest | 是 |

**请求体**（`application/json`，类型 `LimitAdjustRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `userId` | number | 否 | 用户ID | 1 |
| `newLimit` | number | 是 | 新额度 | 15000.00 |
| `reason` | string | 是 | 调整原因 | 信用良好，提升额度 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `newLimit` | 见联调 |
| `oldLimit` | 见联调 |
| `userId` | 见联调 |

---

### 95. 批量调整额度

- **方法**：`POST`　**路径**：`/admin/credit/limits/batch-adjust`
- **鉴权**：需权限点 credit:adjust:write
- **所属模块**：信用额度

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | BatchCreditAdjustRequest | 是 |

**请求体**（`application/json`，类型 `BatchCreditAdjustRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `userIds` | array<number> | 否 | 用户ID列表 | — |
| `mode` | string | 否 | FIXED / INCREASE / DECREASE / PERCENT | — |
| `value` | number | 否 | 调整值（固定额度或增减金额或百分比） | — |
| `reason` | string | 否 | 调整原因 | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `reason` | 见联调 |
| `successCount` | 见联调 |
| `userId` | 见联调 |

---

### 96. 额度调整历史

- **方法**：`GET`　**路径**：`/admin/credit/limits/{userId}/history`
- **鉴权**：需权限点 credit:adjust
- **所属模块**：信用额度

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `page` | query | number | 是 |
| `size` | query | number | 否 |
| `userId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `adjustTime` | 见联调 |
| `id` | 见联调 |
| `newLimit` | 见联调 |
| `oldLimit` | 见联调 |
| `operatorId` | 见联调 |
| `operatorName` | 见联调 |
| `reason` | 见联调 |
| `userId` | 见联调 |

---

### 97. 逾期调额规则

- **方法**：`GET`　**路径**：`/admin/credit/overdue-rules`
- **鉴权**：需权限点 credit:adjust
- **所属模块**：信用额度

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `bCardCoefficients` | 见联调 |
| `level` | 见联调 |
| `overdueRules` | 见联调 |

---


## cs-service

### 98. 待接入/接待中的会话列表

- **方法**：`GET`　**路径**：`/api/v1/admin/cs/conversations`
- **鉴权**：需权限点 cs:ticket
- **所属模块**：人工客服接管（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |

**响应体**

`data` 为数组，元素字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 会话ID | — |
| `userId` | number | 否 | 用户ID | — |
| `status` | string | 否 | 会话状态：OPEN/TRANSFERRED/AGENT/CLOSED | — |
| `lastMessage` | string | 否 | 最近一条消息摘要 | — |
| `createTime` | string(时间) | 否 | 最近一条消息摘要 | — |
| `updateTime` | string(时间) | 否 | 最近一条消息摘要 | — |

---

### 99. 会话聊天记录

- **方法**：`GET`　**路径**：`/api/v1/admin/cs/conversations/{conversationId}/messages`
- **鉴权**：需权限点 cs:ticket
- **所属模块**：人工客服接管（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `conversationId` | path | number | 是 |

**响应体**

`data` 为数组，元素字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `role` | string | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |
| `content` | string | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |
| `intent` | string | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |
| `createTime` | string(时间) | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |

---

### 100. 接管会话（转人工后由人工接入）

- **方法**：`POST`　**路径**：`/api/v1/admin/cs/conversations/{conversationId}/takeover`
- **鉴权**：需权限点 cs:ticket:write
- **所属模块**：人工客服接管（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `conversationId` | path | number | 是 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 会话ID | — |
| `userId` | number | 否 | 用户ID | — |
| `status` | string | 否 | 会话状态：OPEN/TRANSFERRED/AGENT/CLOSED | — |
| `lastMessage` | string | 否 | 最近一条消息摘要 | — |
| `createTime` | string(时间) | 否 | 最近一条消息摘要 | — |
| `updateTime` | string(时间) | 否 | 最近一条消息摘要 | — |

---

### 101. 人工回复用户

- **方法**：`POST`　**路径**：`/api/v1/admin/cs/conversations/{conversationId}/reply`
- **鉴权**：需权限点 cs:ticket:write
- **所属模块**：人工客服接管（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `conversationId` | path | number | 是 |
| `request` | body | AgentReplyRequest | 是 |

**请求体**（`application/json`，类型 `AgentReplyRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `content` | string | 是 | 回复内容 | — |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `role` | string | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |
| `content` | string | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |
| `intent` | string | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |
| `createTime` | string(时间) | 否 | 消息角色：USER/ASSISTANT/AGENT/SYSTEM | — |

---

### 102. 结束会话

- **方法**：`POST`　**路径**：`/api/v1/admin/cs/conversations/{conversationId}/close`
- **鉴权**：需权限点 cs:ticket:write
- **所属模块**：人工客服接管（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `conversationId` | path | number | 是 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 会话ID | — |
| `userId` | number | 否 | 用户ID | — |
| `status` | string | 否 | 会话状态：OPEN/TRANSFERRED/AGENT/CLOSED | — |
| `lastMessage` | string | 否 | 最近一条消息摘要 | — |
| `createTime` | string(时间) | 否 | 最近一条消息摘要 | — |
| `updateTime` | string(时间) | 否 | 最近一条消息摘要 | — |

---

### 103. 工单列表

- **方法**：`GET`　**路径**：`/api/v1/admin/cs/tickets`
- **鉴权**：需权限点 cs:ticket
- **所属模块**：转人工工单（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |
| `pageNum` | query | number | 否 |
| `pageSize` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `records` | 见联调 |
| `total` | 见联调 |

---

### 104. 工单详情

- **方法**：`GET`　**路径**：`/api/v1/admin/cs/tickets/{ticketId}`
- **鉴权**：需权限点 cs:ticket
- **所属模块**：转人工工单（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `ticketId` | path | number | 是 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 工单视图 | — |
| `userId` | number | 否 | 工单视图 | — |
| `conversationId` | number | 否 | 工单视图 | — |
| `category` | string | 否 | 工单视图 | — |
| `description` | string | 否 | 工单视图 | — |
| `involvesMoney` | number | 否 | 工单视图 | — |
| `priority` | string | 否 | 工单视图 | — |
| `bizId` | string | 否 | 工单视图 | — |
| `status` | string | 否 | 工单视图 | — |
| `handlerId` | number | 否 | 工单视图 | — |
| `result` | string | 否 | 工单视图 | — |
| `createTime` | string(时间) | 否 | 工单视图 | — |
| `closeTime` | string(时间) | 否 | 工单视图 | — |

---

### 105. 领取工单（开始处理）

- **方法**：`POST`　**路径**：`/api/v1/admin/cs/tickets/{ticketId}/process`
- **鉴权**：需权限点 cs:ticket:write
- **所属模块**：转人工工单（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `ticketId` | path | number | 是 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 工单视图 | — |
| `userId` | number | 否 | 工单视图 | — |
| `conversationId` | number | 否 | 工单视图 | — |
| `category` | string | 否 | 工单视图 | — |
| `description` | string | 否 | 工单视图 | — |
| `involvesMoney` | number | 否 | 工单视图 | — |
| `priority` | string | 否 | 工单视图 | — |
| `bizId` | string | 否 | 工单视图 | — |
| `status` | string | 否 | 工单视图 | — |
| `handlerId` | number | 否 | 工单视图 | — |
| `result` | string | 否 | 工单视图 | — |
| `createTime` | string(时间) | 否 | 工单视图 | — |
| `closeTime` | string(时间) | 否 | 工单视图 | — |

---

### 106. 关闭工单

- **方法**：`POST`　**路径**：`/api/v1/admin/cs/tickets/{ticketId}/close`
- **鉴权**：需权限点 cs:ticket:write
- **所属模块**：转人工工单（管理端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `ticketId` | path | number | 是 |
| `request` | body | TicketCloseRequest | 是 |

**请求体**（`application/json`，类型 `TicketCloseRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `result` | string | 是 | 处理结果 | — |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 工单视图 | — |
| `userId` | number | 否 | 工单视图 | — |
| `conversationId` | number | 否 | 工单视图 | — |
| `category` | string | 否 | 工单视图 | — |
| `description` | string | 否 | 工单视图 | — |
| `involvesMoney` | number | 否 | 工单视图 | — |
| `priority` | string | 否 | 工单视图 | — |
| `bizId` | string | 否 | 工单视图 | — |
| `status` | string | 否 | 工单视图 | — |
| `handlerId` | number | 否 | 工单视图 | — |
| `result` | string | 否 | 工单视图 | — |
| `createTime` | string(时间) | 否 | 工单视图 | — |
| `closeTime` | string(时间) | 否 | 工单视图 | — |

---

