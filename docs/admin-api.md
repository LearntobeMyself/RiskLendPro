# RiskLendPro 管理员端 API 接口文档

> 基于当前后端代码生成（9 个 Admin Controller，共 **62** 个接口）  
> 源码：`src/main/java/org/example/risklendpro/controller/Admin*.java`  
> 更新日期：2026-06-28

---

## 1. 通用说明

### 1.1 Base URL

| 环境 | Base URL |
|------|----------|
| 本地 | `http://localhost:8080/api/v1` |
| 云端示例 | `http://47.109.109.231:8080/api/v1` |

`context-path` 配置见 `src/main/resources/application.yml` → `server.servlet.context-path: /api/v1`。

### 1.2 鉴权

| 接口 | 鉴权 |
|------|------|
| `POST /admin/register` | 无需 Token |
| `POST /admin/login` | 无需 Token |
| 其余全部管理员接口 | **必须** Header：`Authorization: Bearer {token}` |

登录成功后 `data.token` 为 JWT，默认有效期 7200 秒（`jwt.expire`）。

### 1.3 统一响应体 `CommonResponse`

除文件下载接口外，所有 JSON 接口均返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "success": true,
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 200 成功；其它为失败 |
| message | string | 提示信息 |
| success | boolean | 是否成功 |
| data | T \| null | 业务数据；失败时通常为 null |

### 1.4 分页约定

**格式 A（多数列表）**：`{ list, total }`

**格式 B（MyBatis Page）**：`{ records, total, size, current, pages? }`  
用于：`GET /admin/risk/list`、`GET /admin/loan/pending-list`

**格式 C（数组）**：`data` 直接为数组  
用于：`GET /admin/b-card/monitor`

通用 Query 参数：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| page | int | 1 | 页码 |
| size | int | 10 | 每页条数 |

### 1.5 非 JSON 响应（文件流）

| 接口 | Content-Type | 说明 |
|------|--------------|------|
| `GET /admin/supplement/file/{materialId}` | 材料 MIME | 补资料图片/PDF |
| `GET /admin/system/backups/{backupId}/download` | application/octet-stream | 备份文件 |

### 1.6 路径前缀说明

`/admin/risk/*` 被两个 Controller 共用前缀，职责不同：

| 路径 | Controller | 模块 |
|------|------------|------|
| `/admin/risk/list`、`/report/{applyId}`、`/approve` | AdminController | 风控审批（看板） |
| `/admin/risk/overview`、`/credit-scores` 等 | AdminRiskDataController | 风控数据查询 |

---

## 2. 接口总览（62）

| # | 方法 | 路径 | 鉴权 | 模块 |
|---|------|------|------|------|
| 1 | POST | `/admin/register` | 无 | 认证 |
| 2 | POST | `/admin/login` | 无 | 认证 |
| 3 | GET | `/admin/profile` | JWT | 认证 |
| 4 | GET | `/admin/risk/list` | JWT | 审批与看板 |
| 5 | GET | `/admin/risk/report/{applyId}` | JWT | 审批与看板 |
| 6 | POST | `/admin/risk/approve` | JWT | 审批与看板 |
| 7 | GET | `/admin/bi/vintage` | JWT | 审批与看板 |
| 8 | GET | `/admin/bi/roll-rate` | JWT | 审批与看板 |
| 9 | GET | `/admin/dashboard/stats` | JWT | 审批与看板 |
| 10 | GET | `/admin/loan/pending-list` | JWT | 审批与看板 |
| 11 | POST | `/admin/loan/approve` | JWT | 审批与看板 |
| 12 | GET | `/admin/b-card/monitor` | JWT | 审批与看板 |
| 13 | POST | `/admin/b-card/recalculate/{userId}` | JWT | 审批与看板 |
| 14 | GET | `/admin/users` | JWT | 用户管理 |
| 15 | GET | `/admin/users/export` | JWT | 用户管理 |
| 16 | GET | `/admin/users/{userId}` | JWT | 用户管理 |
| 17 | POST | `/admin/users` | JWT | 用户管理 |
| 18 | PUT | `/admin/users/{userId}` | JWT | 用户管理 |
| 19 | PATCH | `/admin/users/{userId}/status` | JWT | 用户管理 |
| 20 | GET | `/admin/credit/stats` | JWT | 信用额度 |
| 21 | GET | `/admin/credit/limits` | JWT | 信用额度 |
| 22 | POST | `/admin/credit/limits/{userId}/adjust` | JWT | 信用额度 |
| 23 | POST | `/admin/credit/limits/batch-adjust` | JWT | 信用额度 |
| 24 | GET | `/admin/credit/limits/{userId}/history` | JWT | 信用额度 |
| 25 | GET | `/admin/credit/overdue-rules` | JWT | 信用额度 |
| 26 | GET | `/admin/loan/applications/{loanId}` | JWT | 贷款管理 |
| 27 | GET | `/admin/loan/approval-records` | JWT | 贷款管理 |
| 28 | GET | `/admin/loan/records` | JWT | 贷款管理 |
| 29 | GET | `/admin/loan/records/{loanId}` | JWT | 贷款管理 |
| 30 | POST | `/admin/loan/batch-approve` | JWT | 贷款管理 |
| 31 | GET | `/admin/loan/records/export` | JWT | 贷款管理 |
| 32 | GET | `/admin/repayment/summary` | JWT | 还款管理 |
| 33 | GET | `/admin/repayment/plans` | JWT | 还款管理 |
| 34 | GET | `/admin/repayment/plans/{planId}` | JWT | 还款管理 |
| 35 | GET | `/admin/repayment/overdue-stats` | JWT | 还款管理 |
| 36 | GET | `/admin/repayment/plans/{planId}/records` | JWT | 还款管理 |
| 37 | GET | `/admin/repayment/records/stats` | JWT | 还款管理 |
| 38 | GET | `/admin/repayment/actual-records` | JWT | 还款管理 |
| 39 | GET | `/admin/repayment/overdue-records` | JWT | 还款管理 |
| 40 | POST | `/admin/repayment/reminders` | JWT | 还款管理 |
| 41 | POST | `/admin/repayment/report` | JWT | 还款管理 |
| 42 | GET | `/admin/risk/overview` | JWT | 风控数据 |
| 43 | GET | `/admin/risk/credit-scores` | JWT | 风控数据 |
| 44 | GET | `/admin/risk/users/{userId}` | JWT | 风控数据 |
| 45 | GET | `/admin/risk/anti-fraud` | JWT | 风控数据 |
| 46 | POST | `/admin/risk/anti-fraud/{id}/handle` | JWT | 风控数据 |
| 47 | GET | `/admin/risk/multi-loan` | JWT | 风控数据 |
| 48 | GET | `/admin/risk/credit-reports` | JWT | 风控数据 |
| 49 | GET | `/admin/risk/credit-reports/{userId}` | JWT | 风控数据 |
| 50 | GET | `/admin/risk/export` | JWT | 风控数据 |
| 51 | GET | `/admin/system/admins` | JWT | 系统管理 |
| 52 | POST | `/admin/system/admins` | JWT | 系统管理 |
| 53 | PUT | `/admin/system/admins/{adminId}` | JWT | 系统管理 |
| 54 | DELETE | `/admin/system/admins/{adminId}` | JWT | 系统管理 |
| 55 | GET | `/admin/system/operation-logs` | JWT | 系统管理 |
| 56 | GET | `/admin/system/config` | JWT | 系统管理 |
| 57 | PUT | `/admin/system/config` | JWT | 系统管理 |
| 58 | GET | `/admin/system/backups` | JWT | 系统管理 |
| 59 | POST | `/admin/system/backups` | JWT | 系统管理 |
| 60 | POST | `/admin/system/backups/{backupId}/restore` | JWT | 系统管理 |
| 61 | GET | `/admin/system/backups/{backupId}/download` | JWT | 系统管理 |
| 62 | GET | `/admin/supplement/file/{materialId}` | JWT | 补资料 |

> 第 25 条别名：`GET /admin/credit/overdue-adjust-rules` 与 `/overdue-rules` 等价。

---

## 3. 认证模块（AdminAuthController）

### 3.1 POST `/admin/register` — 管理员注册

**鉴权**：无

**Request Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 昵称/登录名 |
| password | string | 是 | 密码 |
| rePassword | string | 是 | 确认密码 |
| phoneNumber | string | 否 | 手机号 |
| email | string | 否 | 邮箱 |

**Response `data`**：`null`

**示例**

```json
// Request
{ "username": "admin2", "password": "Admin123456", "rePassword": "Admin123456", "phoneNumber": "13800138000", "email": "admin2@example.com" }

// Response
{ "code": 200, "message": "注册成功", "success": true, "data": null }
```

---

### 3.2 POST `/admin/login` — 管理员登录

**鉴权**：无

**Request Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 登录名 |
| password | string | 是 | 密码 |

**Response `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 管理员 ID |
| token | string | JWT Token |

**示例**

```json
// Request
{ "username": "admin", "password": "Admin123456" }

// Response
{ "code": 200, "message": "登录成功", "success": true, "data": { "id": 1, "token": "eyJhbG..." } }
```

---

### 3.3 GET `/admin/profile` — 当前管理员资料

**鉴权**：JWT

**Response `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 管理员 ID |
| username | string | 登录名 |
| name | string | 显示名 |
| phoneNumber | string | 手机号 |
| email | string | 邮箱 |
| role | string | 角色 |
| avatar | string | 头像 URL |
| lastLoginTime | string/datetime | 上次登录时间 |

---

## 4. 审批与看板模块（AdminController）

### 4.1 GET `/admin/risk/list` — 风控待审批列表

**Query**

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| page | int | 1 | 页码 |
| size | int | 10 | 每页 |
| status | string | 否 | 筛选状态，如 `MANUAL_REVIEW` |

**Response `data`（MyBatis Page）**

| 字段 | 说明 |
|------|------|
| records | 列表项数组 |
| total | 总条数 |
| size | 每页大小 |
| current | 当前页 |

**records[] 元素**

| 字段 | 类型 | 说明 |
|------|------|------|
| applyId | string | 申请单号 |
| userName | string | 用户姓名 |
| phone | string | 手机号（脱敏） |
| idCard | string | 身份证（脱敏） |
| totalScore | number | A 卡总分 |
| sysDecision | string | 系统决策 |
| status | string | 申请状态 |
| applyTime | datetime | 申请时间 |
| auditRemark | string | 审核备注 |
| riskTags | string[] | 风险标签 |

---

### 4.2 GET `/admin/risk/report/{applyId}` — 风控详细报告

**Path**：`applyId` — 申请单号

**Response `data`**：结构较大，见 [第 11 章 复杂响应](#11-复杂响应结构)。

---

### 4.3 POST `/admin/risk/approve` — 风控终审

**Request Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| applyId | string | 是 | 申请单号 |
| auditResult | string | 是 | `PASS` 通过 / `REJECT` 拒绝 |
| creditLimit | decimal | PASS 时必填 | 最终授信额度 |
| auditRemark | string | 否 | 审批评语 |

**Response `data`**：`null`（审批后邮件通知用户）

**示例**

```json
{ "applyId": "RA202601110", "auditResult": "PASS", "creditLimit": 60000.00, "auditRemark": "材料齐全，通过" }
```

---

### 4.4 GET `/admin/bi/vintage` — Vintage 曲线

**Response `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| months | string[] | 月份轴 |
| vintageData | array | 各月数据 |

**vintageData[] 元素**：`{ month, disbursedAmount, M1Rate, M2Rate, M3Rate }`

---

### 4.5 GET `/admin/bi/roll-rate` — 滚动率

**Response `data`**

```json
{
  "currentStatus": "C",
  "nextMonthStatus": { "C": 0.85, "M1": 0.10, "M2": 0.03, "M3": 0.02 }
}
```

---

### 4.6 GET `/admin/dashboard/stats` — 首页统计

**Response `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalApplications | int | 累计申请数 |
| pendingReview | int | 待人工复核 |
| approvedToday | int | 今日通过 |
| rejectedToday | int | 今日拒绝 |
| totalDisbursed | decimal | 累计放款金额 |
| totalOverdue | int | 逾期笔数 |
| overdueRate | decimal | 逾期率 |

---

### 4.7 GET `/admin/loan/pending-list` — 待审批贷款（额度外）

**Query**：`page`, `size`

**Response `data`（MyBatis Page）**

**records[] 元素**

| 字段 | 类型 | 说明 |
|------|------|------|
| loanId | long | 贷款 ID |
| userId | long | 用户 ID |
| userName | string | 姓名 |
| phone | string | 手机 |
| idCard | string | 身份证 |
| currentLimit | decimal | 当前额度 |
| exceedAmount | decimal | 超出额度部分 |
| amount | decimal | 申请金额 |
| termMonths | int | 期数 |
| repaymentMethod | string | 还款方式 |
| applyTime | datetime | 申请时间 |
| status | string | 状态 |

---

### 4.8 POST `/admin/loan/approve` — 审批贷款申请

**Request Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| loanId | long | 是 | 贷款 ID |
| approveResult | string | 是 | `APPROVE` / `REJECT` |
| additionalLimit | decimal | APPROVE 时 | 额外批准额度 |
| approveRemark | string | 否 | 审批备注 |

**Response `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| loanId | long | 贷款 ID |
| userId | long | 用户 ID |
| originalLimit | decimal | 原额度 |
| additionalLimit | decimal | 额外额度 |
| totalLimit | decimal | 调整后总额度 |
| actualDisbursedAmount | decimal | 实际放款 |
| status | string | 贷款状态 |
| approveTime | datetime | 审批时间 |
| rejectReason | string | 拒绝原因 |
| emailSent | boolean | 是否已发邮件 |

---

### 4.9 GET `/admin/b-card/monitor` — B 卡贷后监控

**Response `data`**：数组（非分页），见 [11.2 B 卡监控](#112-get-adminb-cardmonitor)。

---

### 4.10 POST `/admin/b-card/recalculate/{userId}` — 手动重算 B 卡

**Path**：`userId` — 用户 ID

**Response `data`**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | long | 用户 ID |
| bScore | decimal | 重算后 B 分 |
| bScoreUpdatedAt | datetime | 更新时间 |
| limitMultiplier | decimal | 额度系数 |

**示例**

```json
{ "code": 200, "message": "重算成功", "success": true, "data": { "userId": 105, "bScore": 514.0, "bScoreUpdatedAt": "2026-06-28T13:09:53", "limitMultiplier": 0.5 } }
```

---

## 5. 用户管理模块（AdminUserController）

### 5.1 GET `/admin/users` — 用户列表

**Query**

| 参数 | 类型 | 说明 |
|------|------|------|
| page, size | int | 分页 |
| status / accountStatus | string | 账户状态（别名，优先 accountStatus） |
| name / realName | string | 姓名模糊（别名，优先 realName） |
| phone / phoneNumber | string | 手机号（别名，优先 phoneNumber） |

**Response `data`**：`{ list, total }`

**list[] 元素**

| 字段 | 类型 | 说明 |
|------|------|------|
| id / userId | long | 用户 ID |
| name / realName | string | 姓名 |
| phone / phoneNumber | string | 手机号 |
| idCard | string | 身份证（脱敏） |
| creditScore | number | A 卡分 |
| creditLimit | decimal | 授信额度 |
| loanCount | int | 借款笔数 |
| totalLoan | decimal | 累计借款 |
| status / accountStatus | string | 账户状态 |
| registerTime / createTime | datetime | 注册时间 |
| avatar | string | 头像 |

---

### 5.2 GET `/admin/users/export` — 导出用户

**Query**：同列表筛选（无分页）

**Response `data`**

| 字段 | 说明 |
|------|------|
| downloadUrl | 相对下载路径 |
| reportId | 导出任务 ID |

---

### 5.3 GET `/admin/users/{userId}` — 用户详情

**Response `data`**：列表字段 + `email`、完整 `idCard`、`lastLoginTime`

---

### 5.4 POST `/admin/users` — 新增用户

**Request Body**

| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 姓名 |
| phone | string | 手机号 |
| email | string | 邮箱 |
| idCard | string | 身份证号 |
| status | string | 账户状态 |

**Response `data`**：`{ userId }`

---

### 5.5 PUT `/admin/users/{userId}` — 编辑用户

**Request Body**：同创建

**Response `data`**：`{ userId }`

---

### 5.6 PATCH `/admin/users/{userId}/status` — 更新用户状态

**Request Body**

| 字段 | 类型 | 说明 |
|------|------|------|
| status | string | `ACTIVE` / `DISABLED` / `FROZEN` |
| reason | string | 操作原因 |
| enabled | boolean | 兼容：`true`=ACTIVE，`false`=DISABLED |

**Response `data`**：`{ userId, status }`

---

## 6. 信用额度模块（AdminCreditController）

### 6.1 GET `/admin/credit/stats` — 额度统计

**Response `data`**

| 字段 | 说明 |
|------|------|
| totalCreditLimit | 总授信 |
| usedCreditLimit | 已用 |
| availableCreditLimit | 可用 |
| averageUsageRate | 平均使用率 |
| userCount | 用户数 |
| trends | 趋势对象（可为空） |

---

### 6.2 GET `/admin/credit/limits` — 额度列表

**Query**：`page`, `size`, `userName`, `phone`, `hasOverdue` (boolean), `status`

**Response `data`**：`{ list, total }`

**list[] 元素**

| 字段 | 说明 |
|------|------|
| userId, userName, phone, idCard | 用户标识 |
| creditScore | A 卡分 |
| totalLimit, usedLimit, remainingLimit, availableLimit | 额度 |
| usageRate | 使用率 |
| status | 额度状态 |
| bScore, bCardEnabled | B 卡 |
| hasOverdue, overdueAmount | 逾期 |
| lastUpdateTime, lastAdjustTime | 时间 |
| avatar | 头像 |

---

### 6.3 POST `/admin/credit/limits/{userId}/adjust` — 调整单用户额度

**Path**：`userId`

**Request Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| newLimit | decimal | 是 | 新额度 |
| reason | string | 否 | 原因 |
| userId | long | 否 | Body 中可省略（以 Path 为准） |

**Response `data`**：`{ userId, oldLimit, newLimit }`

---

### 6.4 POST `/admin/credit/limits/batch-adjust` — 批量调额

**Request Body**

| 字段 | 类型 | 说明 |
|------|------|------|
| userIds | long[] | 用户 ID 列表 |
| mode | string | `FIXED` / `INCREASE` / `DECREASE` / `PERCENT` |
| value | decimal | 固定值/增减额/百分比 |
| reason | string | 原因 |

**Response `data`**

| 字段 | 说明 |
|------|------|
| successCount | 成功数 |
| failCount | 失败数 |
| failedItems | `[{ userId, reason }]` |

---

### 6.5 GET `/admin/credit/limits/{userId}/history` — 额度调整历史

**Query**：`page`, `size`

**Response `data`**：`{ list, total }`

**list[] 元素**：`id`, `userId`, `oldLimit`, `newLimit`, `reason`, `operatorId`, `operatorName`, `adjustTime`

---

### 6.6 GET `/admin/credit/overdue-rules` — 逾期/B 卡系数规则（展示用）

**Response `data`**

```json
{
  "bCardCoefficients": [
    { "minScore": 700, "maxScore": 850, "multiplier": 1.0 },
    { "minScore": 600, "maxScore": 699, "multiplier": 0.9 },
    { "minScore": 0, "maxScore": 599, "multiplier": 0.7 }
  ],
  "overdueRules": [
    { "level": "M1", "multiplier": 0.8, "description": "逾期M1降额20%" },
    { "level": "M2", "multiplier": 0.5, "description": "逾期M2降额50%" }
  ]
}
```

> 注意：页头真实 `limitMultiplier` 来自 `BehaviorScoreService.resolveLimitMultiplier`（DB 阈值），与本静态表可能不一致。

---

## 7. 贷款管理模块（AdminLoanController）

### 7.1 GET `/admin/loan/applications/{loanId}` — 贷款申请详情

**Response `data`**：`loanId`, `userId`, `userName`, `phone`, `idCard`, `amount`, `termMonths`, `purpose`, `interestRate`, `repaymentMethod`, `status`, `applyTime`, `creditScore`, `riskReportId`, `currentLimit`, `exceedAmount`, `materials[]`

---

### 7.2 GET `/admin/loan/approval-records` — 审批记录

**Query**：`page`, `size`, `userId`, `loanId`, `status`, `startDate`, `endDate`

**list[] 元素**：`id`, `loanId`, `userId`, `reviewerId`, `reviewerName`, `applicantName`, `status`, `comment`, `createTime`, `updateTime`

---

### 7.3 GET `/admin/loan/records` — 借款记录列表

**Query**：`page`, `size`, `status`, `userName`, `startDate`, `endDate`

**list[] 元素**

| 字段 | 说明 |
|------|------|
| loanId, userId, userName, phone | 基本信息 |
| loanAmount, loanTerm, interestRate | 贷款 |
| loanTime, dueTime | 时间 |
| paidAmount | 已还 |
| status | `REPAYING` / `SETTLED` / `OVERDUE` |
| overdueDays | 逾期天数 |
| avatar | 头像 |

---

### 7.4 GET `/admin/loan/records/{loanId}` — 借款详情

**Response**：列表字段 + `idCard`, `repaymentMethod`, `remainingAmount`, `paidAmount`

---

### 7.5 POST `/admin/loan/batch-approve` — 批量审批

**Request Body**

| 字段 | 类型 | 说明 |
|------|------|------|
| loanIds | long[] | 贷款 ID 列表 |
| approveResult | string | `APPROVE` / `REJECT` |
| additionalLimit | decimal | 批量通过时额外额度 |
| approveRemark | string | 备注 |

**Response `data`**：`{ successCount, failCount, failedItems: [{ loanId, reason }] }`

---

### 7.6 GET `/admin/loan/records/export` — 导出借款记录

**Query**：`status`, `userName`, `startDate`, `endDate`

**Response `data`**：`{ downloadUrl, reportId }`

---

## 8. 还款管理模块（AdminRepaymentController）

### 8.1 GET `/admin/repayment/summary` — 还款汇总

**Response `data`**：`receivableThisMonth`, `receivedThisMonth`, `pendingAmount`, `overdueAmount`

---

### 8.2 GET `/admin/repayment/plans` — 还款计划列表

**Query**：`page`, `size`, `loanId`, `userName`, `status`

**list[] 元素（plan）**

| 字段 | 说明 |
|------|------|
| planId, loanId, userId, userName | 标识 |
| totalAmount, principalAmount, interestAmount | 金额 |
| repaymentMethod | 还款方式 |
| totalPeriods, currentPeriod | 期数 |
| status, statusDesc | 计划状态 |
| startDate, endDate | 起止 |
| remainingAmount, paidAmount, paidPeriods | 进度 |
| progressPercentage | 完成百分比 |
| createTime, updateTime | 时间 |

---

### 8.3 GET `/admin/repayment/plans/{planId}` — 计划详情

**Response**：plan 字段 + `repaymentRecords[]`, `overdueRecords[]`, `completedRecords[]`, `activeRecords[]`

**record 元素**：`recordId`, `planId`, `period`, `amount`, `principal`, `interest`, `actualAmount`, `dueDate`, `repaymentDate`, `status`, `overdueDays`, `createTime`

---

### 8.4 GET `/admin/repayment/overdue-stats` — 逾期统计

**Response `data`**：`{ totalCount, overdueCount }`

---

### 8.5 GET `/admin/repayment/plans/{planId}/records` — 计划期次列表

**Query**：`page`, `size`, `status`

**Response `data`**：`{ list: record[], total }`

---

### 8.6 GET `/admin/repayment/records/stats` — 还款记录统计

**Query**：`planId`（可选）

**Response `data`**：`{ totalCount, completedCount }`

---

### 8.7 GET `/admin/repayment/actual-records` — 实际还款流水

**Query**：`page`, `size`, `loanId`, `userName`, `startDate`, `endDate`

**list[] 元素**：`id`, `loanId`, `userName`, `repayDate`, `repayAmount`, `repayType`, `status`, `operator`

---

### 8.8 GET `/admin/repayment/overdue-records` — 逾期记录

**Query**：`page`, `size`

**list[] 元素**：`id`, `loanId`, `userName`, `overdueAmount`, `overdueDays`, `overdueDate`, `contactTimes`, `lastContact`, `status`

---

### 8.9 POST `/admin/repayment/reminders` — 发送还款提醒

**Request Body**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | long | 用户 ID |
| loanId | long | 贷款 ID |
| planId | long | 计划 ID |
| channel | string | 渠道（如 EMAIL） |
| message | string | 提醒内容 |

**Response `data`**：`{ reminderId, sendTime }`

---

### 8.10 POST `/admin/repayment/report` — 生成还款报表

**Request Body**

| 字段 | 类型 | 说明 |
|------|------|------|
| startDate | string | 开始日期 yyyy-MM-dd |
| endDate | string | 结束日期 |
| type | string | 报表类型 |

**Response `data`**：`{ downloadUrl, reportId }`

---

## 9. 风控数据模块（AdminRiskDataController）

> 与第 4 章「风控审批」不同，本章为数据查询/分析。

### 9.1 GET `/admin/risk/overview` — 风控概览

**Response `data`**

| 字段 | 说明 |
|------|------|
| totalUsers | 用户总数 |
| aScoreDistribution | `[{ level, count }]` |
| bScoreDistribution | `[{ level, count }]` |

---

### 9.2 GET `/admin/risk/credit-scores` — 信用评分列表

**Query**：`page`, `size`, `userName`, `riskLevel`

**list[] 元素**：`userId`, `userName`, `applyId`, `totalScore`, `creditLimit`, `status`, `riskLevel`, `submitTime`

---

### 9.3 GET `/admin/risk/users/{userId}` — 用户风控详情

**Response `data`**：无评估时 `{ userId, userName, hasRiskAssessment: false, externalFeaturesDb?, externalFeatures? }`；有评估时结构同 [风控报告](#111-get-adminriskreportapplyid)。

---

### 9.4 GET `/admin/risk/anti-fraud` — 反欺诈告警

**Query**：`page`, `size`, `status`

**list[] 元素**：`id`, `userId`, `userName`, `alertType`, `description`, `riskLevel`, `status`, `createTime`

---

### 9.5 POST `/admin/risk/anti-fraud/{id}/handle` — 处理反欺诈

**Request Body**

| 字段 | 类型 | 说明 |
|------|------|------|
| action | string | `CONFIRM` / `DISMISS` / `ESCALATE` |
| remark | string | 处理备注 |

**Response `data`**：`{ id, status }`

---

### 9.6 GET `/admin/risk/multi-loan` — 多头借贷

**Query**：`page`, `size`, `minActiveLoans`

**list[] 元素**：`userId`, `userName`, `idCard`, `activeLoansCount`, `creditBureauMon`, `riskLevel`

---

### 9.7 GET `/admin/risk/credit-reports` — 征信/评估报告列表

**Query**：`page`, `size`, `status`

**list[] 元素**：`applyId`, `userId`, `userName`, `totalScore`, `status`, `sysDecision`, `submitTime`

---

### 9.8 GET `/admin/risk/credit-reports/{userId}` — 用户报告详情

**Response**：同 `GET /admin/risk/users/{userId}` / 风控报告结构。

---

### 9.9 GET `/admin/risk/export` — 风控数据导出

**Query**：`status`（可选）

**Response `data`**：`{ downloadUrl, reportId }`

---

## 10. 系统管理模块（AdminSystemController）

### 10.1 GET `/admin/system/admins` — 管理员列表

**Query**：`page`, `size`

**list[] 元素**：`id`, `username`, `phoneNumber`, `email`, `role`, `createTime`

---

### 10.2 POST `/admin/system/admins` — 新增管理员

**Request Body**：`username`, `password`, `phoneNumber`, `email`

**Response `data`**：`{ adminId }`

---

### 10.3 PUT `/admin/system/admins/{adminId}` — 更新管理员

**Request Body**：`username`, `password`, `phoneNumber`, `email`（密码可选更新）

**Response `data`**：`{ adminId }`

---

### 10.4 DELETE `/admin/system/admins/{adminId}` — 删除管理员

**Response `data`**：`null`

---

### 10.5 GET `/admin/system/operation-logs` — 操作日志

**Query**：`page`, `size`, `module`, `startDate`, `endDate`

**list[] 元素**：`id`, `module`, `action`, `operatorId`, `operatorName`, `detail`, `createTime`

---

### 10.6 GET `/admin/system/config` — 读取系统配置

**Response `data`**：`emailEnabled`, `logRetentionDays`, `backupRetentionDays`, `systemName`

---

### 10.7 PUT `/admin/system/config` — 更新系统配置

**Request Body**：同上字段

**Response `data`**：更新后的配置对象

---

### 10.8 GET `/admin/system/backups` — 备份列表

**Query**：`page`, `size`

**list[] 元素**：`id`, `filename`, `size`, `createTime`, `status`

---

### 10.9 POST `/admin/system/backups` — 创建备份

**Response `data`**：`{ backupId, filename, status }`

---

### 10.10 POST `/admin/system/backups/{backupId}/restore` — 恢复备份

**Response `data`**：`{ backupId, status, message }`

---

### 10.11 GET `/admin/system/backups/{backupId}/download` — 下载备份

**响应**：二进制文件流，`Content-Disposition: attachment`

---

## 11. 补资料模块（AdminSupplementController）

### 11.1 GET `/admin/supplement/file/{materialId}` — 下载/预览材料

**Path**：`materialId` — `risk_supplement_material.id`

**Query**

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| inline | boolean | false | `true` 浏览器内预览；`false` 下载 |

**响应**：文件流，`Content-Type` 为材料 MIME；`Content-Disposition` 含原始文件名。

**错误**：材料不存在或 `stored_path` 文件缺失 → HTTP 404

**stored_path 示例**：`/app/upload/risk-supplement/L1782645053994aaf1/15ae39fc....jpg`

---

## 12. 复杂响应结构

### 12.1 GET `/admin/risk/report/{applyId}`

**顶层字段（并集，按缓存/DB 情况出现）**

| 字段 | 说明 |
|------|------|
| reportCacheHit | 是否命中 Redis 报告缓存 |
| applyId | 申请单号 |
| reportCachedAt | 缓存时间 |
| reportDisplay | 前端展示结构化数据（见下） |
| totalScore | A 卡总分 |
| sysDecision / systemDecision | 系统决策 |
| scoreThresholds | `{ rejectBelow, approveFrom, min, max }` |
| scoreZone | 分数区间标签 |
| ruleGate, rejectGate | 规则门禁 |
| supplementStatus | 补资料状态 |
| supplementRequirements | 需补材料清单 |
| supplementMaterials | 已上传材料 |
| finalStatus, auditRemark, riskTags | 终审相关 |
| outcomeSummary, incomeVerification, blacklistCheck | 结果摘要 |
| modelVersion, thirdPartyLinked, scored, suggestedAmount | 模型元数据 |
| scoreDetails | 评分明细数组 |
| userDetails | 用户申请信息 |
| externalFeatures | 外部特征 |
| scoreRecomputed, cacheWriteFailed | 技术标记 |

**scoreDetails[]**

| 字段 | 说明 |
|------|------|
| feature | 特征码 |
| value | 特征值 |
| weight | 权重 |
| contribution | 贡献分 |
| description | 描述 |
| rawValueText, woe | 可选展示 |

**supplementMaterials[]**

| 字段 | 说明 |
|------|------|
| materialId | 材料 ID |
| materialType | 类型码 |
| originalName | 原始文件名 |
| fileSize | 字节 |
| mimeType | MIME |
| remark | 用户备注 |
| uploadTime, expireAt | 时间 |
| downloadUrl | `/admin/supplement/file/{materialId}` |

**reportDisplay**

| 字段 | 说明 |
|------|------|
| scoreItems[] | 表格行：label, featureCode, displayValue, contribution, direction 等 |
| scoreSummary | positiveContribution, negativeContribution 等 |
| externalItems[], userItems[] | label, value, group, hint |

**示例（精简）**

```json
{
  "code": 200,
  "success": true,
  "data": {
    "applyId": "RA202601110",
    "reportCacheHit": true,
    "totalScore": 682.5,
    "sysDecision": "MANUAL_REVIEW",
    "supplementMaterials": [
      {
        "materialId": 12,
        "materialType": "INCOME_PROOF",
        "originalName": "salary.jpg",
        "downloadUrl": "/admin/supplement/file/12"
      }
    ],
    "reportDisplay": {
      "scoreItems": [{ "label": "年龄", "contribution": 12.3 }],
      "scoreSummary": { "positiveContribution": 45.0, "negativeContribution": -10.2 }
    }
  }
}
```

---

### 12.2 GET `/admin/b-card/monitor`

**Response `data`**：`Array<MonitorRow>`（按 watchLevel 紧迫度排序）

**MonitorRow 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | long | 用户 ID |
| userName | string | 姓名 |
| phone | string | 手机（脱敏） |
| idCard | string | 身份证（脱敏） |
| bScore | decimal | 当前 B 分 |
| bScoreUpdatedAt | datetime | B 分更新时间 |
| totalLimit | decimal | 总额度 |
| hasOverdue | boolean | 额度表逾期标记 |
| activePlanCount | int | 未结清计划数 |
| baseScore | decimal | 最新日志：基线分 |
| deltaScore | decimal | 最新日志：修正分 |
| liveFeatures | object/string | 行为特征 JSON |
| planId | long | 展示的计划 ID |
| planStatus | string | 计划状态 ACTIVE/OVERDUE 等 |
| overdueLevel | string | N/M1/M2/M3/M4 |
| overdueDays | int | 计划逾期天数 |
| dueDate | datetime | 当期应还日 |
| currentPeriod | int | 当期期数 |
| recordStatus | string | 当期 record 状态 |
| daysToDue | int | 距到期天数（负=已过期） |
| watchLevel | string | 预警等级（见附录） |
| limitMultiplier | decimal | 额度系数 |

**liveFeatures 对象**

| 字段 | 说明 |
|------|------|
| maxOverdueDays | 最大逾期天数 |
| overduePeriodCount | 逾期计划/期数 |
| onTimeRate | 按时还款率 0~1 |

**修正分公式**：`delta = -maxOverdueDays×3 - overduePeriodCount×12 + onTimeRate×40`  
**B 分**：`clamp(baseScore + delta)`，范围 350~950

**示例**

```json
{
  "code": 200,
  "success": true,
  "data": [
    {
      "userId": 105,
      "userName": "陈静",
      "bScore": 514.0,
      "baseScore": 700.0,
      "deltaScore": -186.0,
      "liveFeatures": { "maxOverdueDays": 58, "overduePeriodCount": 1, "onTimeRate": 0.0 },
      "watchLevel": "OVERDUE",
      "daysToDue": -58,
      "limitMultiplier": 0.5,
      "planStatus": "OVERDUE",
      "overdueDays": 58
    }
  ]
}
```

---

## 13. 附录

### 13.1 枚举与常用状态

**风控审批 auditResult**：`PASS`, `REJECT`

**贷款审批 approveResult**：`APPROVE`, `REJECT`

**用户 accountStatus**：`ACTIVE`, `DISABLED`, `FROZEN`

**批量调额 mode**：`FIXED`, `INCREASE`, `DECREASE`, `PERCENT`

**B 卡 watchLevel**：`NORMAL`, `DUE_SOON`, `DUE_TODAY`, `OVERDUE`

**还款 plan status**：`ACTIVE`, `OVERDUE`, `COMPLETED`

**还款 record status**：`PENDING`, `COMPLETED`, `OVERDUE`

**反欺诈 action**：`CONFIRM`, `DISMISS`, `ESCALATE`

### 13.2 导出/下载类接口汇总

| 接口 | data 字段 | 前端用法 |
|------|-----------|----------|
| GET /admin/users/export | downloadUrl, reportId | downloadWithAuth |
| GET /admin/loan/records/export | downloadUrl, reportId | downloadWithAuth |
| GET /admin/risk/export | downloadUrl, reportId | downloadWithAuth |
| POST /admin/repayment/report | downloadUrl, reportId | 返回 URL 后下载 |
| GET /admin/supplement/file/{id} | 文件流 | 带 Token 的 URL |
| GET /admin/system/backups/{id}/download | 文件流 | downloadWithAuth |

### 13.3 前端 API 模块对照

| 前端文件 | 覆盖接口 |
|----------|----------|
| `frontend/packages/shared/src/api/adminAuth.ts` | #1-3 |
| `frontend/packages/shared/src/api/adminCore.ts` | #4-13 |
| `frontend/packages/shared/src/api/adminUsers.ts` | #14-19 |
| `frontend/packages/shared/src/api/adminCredit.ts` | #20-25 |
| `frontend/packages/shared/src/api/adminLoan.ts` | #26-31 |
| `frontend/packages/shared/src/api/adminRepayment.ts` | #32-41 |
| `frontend/packages/shared/src/api/adminRiskData.ts` | #42-50 |
| `frontend/packages/shared/src/api/adminSystem.ts` | #51-61 |
| `frontend/packages/shared/src/api/adminSupplement.ts` | #62（URL 拼接） |

### 13.4 相关源码索引

| 类型 | 路径 |
|------|------|
| Controllers | `src/main/java/org/example/risklendpro/controller/Admin*.java` |
| Request DTO | `src/main/java/org/example/risklendpro/pojo/request/` |
| 核心 Service | `AdminServiceImpl`, `AdminUserServiceImpl`, `AdminCreditQueryServiceImpl`, `AdminLoanQueryServiceImpl`, `AdminRepaymentQueryServiceImpl`, `AdminRiskDataServiceImpl`, `AdminSystemServiceImpl` |
| 安全配置 | `src/main/java/org/example/risklendpro/config/SecurityConfig.java` |

---

*文档结束*
