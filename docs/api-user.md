# RiskLendPro 后端接口文档 — 用户端

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
- 本端接口均需登录，角色为 `USER`（注册、登录本身无需携带 token）。

### 分页结构
- 部分列表接口返回 `{ "list": [ ... ], "total": N }`；
- 部分返回 `{ "records": [ ... ], "total": N, "size": N, "current": N, "pages": N }`；
- 产品列表返回 `{ "list": [ ... ], "total": N, "pageNum": N, "pageSize": N }`。
- 具体以各接口「响应体」说明为准。


## user-service

### 1. 查看用户额度

- **方法**：`GET`　**路径**：`/user/credit-limit`
- **鉴权**：需登录（角色 USER）
- **所属模块**：贷后监控与额度管理模块

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `userId` | number | 否 | — | — |
| `totalLimit` | number | 否 | — | — |
| `usedLimit` | number | 否 | — | — |
| `remainingLimit` | number | 否 | — | — |
| `overdueAmount` | number | 否 | — | — |
| `hasOverdue` | boolean | 否 | — | — |
| `behaviorScore` | number | 否 | — | — |
| `bScoreUpdatedAt` | number | 否 | — | — |
| `bCardEnabled` | boolean | 否 | — | — |
| `lastUpdateTime` | number | 否 | — | — |

---

### 2. 用户注册

- **方法**：`POST`　**路径**：`/auth/register`
- **鉴权**：登录（未限角色）
- **所属模块**：认证模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | UserRegisterRequest | 是 |

**请求体**（`application/json`，类型 `UserRegisterRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `realName` | string | 否 | 用户姓名 | 陈优质 |
| `phoneNumber` | string | 是 | 用户手机号 | 13800148001 |
| `email` | string | 是 | 用户邮箱 | chen_a@example.com |
| `idCard` | string | 否 | 用户身份证号（与 user_external_features.id_card 及风控申请一致） | 110101198503151001 |
| `password` | string | 是 | 用户密码 | Test123456 |
| `repassword` | string | 是 | 用户重新输入密码 | Test123456 |

**响应体**

无 `data` 字段（仅返回 `code`/`message`）。

---

### 3. 用户登录

- **方法**：`POST`　**路径**：`/auth/login`
- **鉴权**：登录（未限角色）
- **所属模块**：认证模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | UserLoginRequest | 是 |

**请求体**（`application/json`，类型 `UserLoginRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `phoneNumber` | string | 是 | 用户手机号 | 13800138001 |
| `password` | string | 是 | 用户密码 | 123456 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `id` | number | 否 | 用户ID | — |
| `token` | string | 否 | JWT令牌 | — |
| `role` | string | 否 | 用户角色 | — |
| `assessmentStatus` | string | 否 | 评估状态：NOT_ASSESSED=未评估, WAITING/MANUAL_REVIEW=评估中, FINAL_PASS=已通过, SYSTEM_REJECT/FINAL_REJECT=已拒绝 | — |

---


## risk-service

### 4. 提交风控评估申请

- **方法**：`POST`　**路径**：`/risk/assessment/submit`
- **鉴权**：需登录（角色 USER）
- **所属模块**：授信评估模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | RiskAssessmentRequest | 是 |

**请求体**（`application/json`，类型 `RiskAssessmentRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `idCard` | string | 否 | 身份证号（唯一标识） | 110101198503151001 |
| `name` | string | 否 | 用户姓名 | 陈优质 |
| `phone` | string | 否 | 手机号码 | 13800148001 |
| `email` | string | 否 | 接收结果的邮箱 | chen_a@example.com |
| `gender` | number | 否 | 0-女, 1-男 | 1 |
| `birthday` | string | 否 | 出生日期 YYYY-MM-DD | 1985-03-15 |
| `education` | string | 否 | 学历 | 本科 |
| `marriage` | string | 否 | 婚姻状况 | 已婚 |
| `jobType` | string | 否 | 职业类型 | 企事业单位 |
| `monthlyIncome` | string | 否 | 月收入 | 15000以上 |
| `hasHouse` | boolean | 否 | 是否有房 | true |
| `hasCar` | boolean | 否 | 是否有车 | true |
| `contactPhone` | string | 否 | 紧急联系人电话 | 13800148000 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `applyId` | string | 否 | 申请ID | — |
| `submitTime` | string(时间) | 否 | 提交时间 | — |
| `status` | string | 否 | 状态 | — |

---

### 5. 可否提交新评估

- **方法**：`GET`　**路径**：`/risk/assessment/submit-eligibility`
- **鉴权**：需登录（角色 USER）
- **所属模块**：授信评估模块

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `canSubmit` | boolean | 否 | 是否允许提交新申请 | — |
| `reason` | string | 否 | 不可提交时的说明 | — |
| `existingApplyId` | string | 否 | 已有申请的 applyId | — |
| `existingStatus` | string | 否 | 已有申请的状态 | — |
| `retryAfterDays` | number | 否 | 冷却剩余天数（仅 SYSTEM_REJECT 30 天内） | — |

---

### 6. 轮询查询评估状态

- **方法**：`GET`　**路径**：`/risk/assessment/status`
- **鉴权**：需登录（角色 USER）
- **所属模块**：授信评估模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `applyId` | query | string | 是 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `applyId` | string | 否 | 申请ID | — |
| `status` | string | 否 | 状态 | — |
| `statusTitle` | string | 否 | 状态标题 | — |
| `isFinal` | boolean | 否 | 是否终态 | — |
| `supplementStatus` | string | 否 | 补充材料状态: NONE/REQUIRED/SUBMITTED | — |
| `requiredMaterials` | array<SupplementRequirement> | 否 | 需上传材料清单 | — |
| `supplementDeadline` | string(时间) | 否 | 材料上传截止时间 | — |
| `totalScore` | number | 否 | 信用总分 | — |
| `sysDecision` | string | 否 | 模型决策: APPROVE/MANUAL_REVIEW/REJECT | — |
| `ruleTrigger` | string | 否 | 规则触发: BLACKLIST_L2/INCOME_OUTLIER/SCORE_ONLY，无则 null | — |
| `uploadedMaterialTypes` | array<string> | 否 | 已上传材料类型 | — |

---

### 7. 获取最终额度结果

- **方法**：`GET`　**路径**：`/risk/assessment/result`
- **鉴权**：需登录（角色 USER）
- **所属模块**：授信评估模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `applyId` | query | string | 是 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `applyId` | string | 否 | 申请ID | — |
| `totalScore` | number | 否 | 风控总分 | — |
| `creditLimit` | number | 否 | 授信额度 | — |
| `expireDate` | string(时间) | 否 | 额度失效日期 | — |
| `status` | string | 否 | 状态 | — |
| `approvalTime` | string(时间) | 否 | 审批时间 | — |

---

### 8. 查询需补充材料清单

- **方法**：`GET`　**路径**：`/risk/assessment/supplement/requirements`
- **鉴权**：需登录（角色 USER）
- **所属模块**：补充材料模块

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applyId` | 见联调 |
| `requiredMaterials` | 见联调 |
| `status` | 见联调 |
| `supplementDeadline` | 见联调 |
| `supplementStatus` | 见联调 |
| `uploadedMaterialTypes` | 见联调 |
| `uploadedMaterials` | 见联调 |

---

### 9. 上传补充材料

- **方法**：`POST`　**路径**：`/risk/assessment/supplement/upload`
- **鉴权**：需登录（角色 USER）
- **所属模块**：补充材料模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `materialType` | query | string | 是 |
| `file` | formData | file | 是 |
| `remark` | query | string | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `applyId` | 见联调 |
| `expireAt` | 见联调 |
| `materialId` | 见联调 |
| `materialType` | 见联调 |
| `supplementStatus` | 见联调 |
| `verification` | 见联调 |
| `verificationSkipped` | 见联调 |

---


## loan-service

### 10. 发起借款请求

- **方法**：`POST`　**路径**：`/loan/request`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借款/支用模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | LoanRequest | 是 |

**请求体**（`application/json`，类型 `LoanRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `amount` | number | 是 | 借款金额 | 5000.00 |
| `termMonths` | number | 是 | 还款期限（月） | 12 |
| `repaymentMethod` | string | 否 | 还款方式（等额本息/等额本金/先息后本），也可传产品枚举码 EQUAL_INSTALLMENT 等 | 等额本息 |
| `productId` | number | 否 | 目录产品ID；为空则为直接借款 | — |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `loanId` | number | 否 | 借款ID | — |
| `userId` | number | 否 | 用户ID | — |
| `amount` | number | 否 | 借款金额 | — |
| `termMonths` | number | 否 | 还款期限（月） | — |
| `interestRate` | number | 否 | 利率 | — |
| `repaymentMethod` | string | 否 | 还款方式 | — |
| `status` | string | 否 | 状态 | — |
| `disbursementTime` | string(时间) | 否 | 放款时间 | — |
| `applyTime` | string(时间) | 否 | 申请时间 | — |
| `autoApproved` | boolean | 否 | 是否自动审批 | — |
| `remark` | string | 否 | 备注 | — |
| `productId` | number | 否 | 目录产品ID，直接借款为空 | — |
| `productName` | string | 否 | 申请时产品名称快照 | — |
| `applyChannel` | string | 否 | 申请通路 DIRECT/PRODUCT | — |

---

### 11. 获取用户借款记录

- **方法**：`GET`　**路径**：`/loan/user/history`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借款/支用模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `pageNum` | query | number | 是 |
| `pageSize` | query | number | 否 |

**响应体**

`data` 为分页对象，结构：`{ records: [借款记录], total, size, current, pages }`，其中 `records` 元素字段见上文「借款响应」字段表。

---

### 12. 获取用户贷款申请记录

- **方法**：`GET`　**路径**：`/loan/user/applications`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借款/支用模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |
| `pageNum` | query | number | 否 |
| `pageSize` | query | number | 否 |

**响应体**

`data` 为分页对象，结构：`{ records: [申请记录], total, size, current, pages }`，其中 `records` 元素字段见上文「借款响应」字段表。

---

### 13. U1 获取分类与筛选项

- **方法**：`GET`　**路径**：`/loan/products/filters`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `categories` | 见联调 |
| `categoryCode` | 见联调 |
| `categoryName` | 见联调 |
| `commonTags` | 见联调 |
| `description` | 见联调 |
| `icon` | 见联调 |
| `institutionTypes` | 见联调 |
| `mortgageOptions` | 见联调 |
| `productCount` | 见联调 |
| `repaymentMethods` | 见联调 |
| `sortOptions` | 见联调 |
| `tags` | 见联调 |
| `targetGroups` | 见联调 |

---

### 14. U2 产品列表

- **方法**：`GET`　**路径**：`/loan/products`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `keyword` | query | string | 是 |
| `categoryCode` | query | string | 否 |
| `institutionTypes` | query | array<string> | 否 |
| `amount` | query | number | 否 |
| `maxRate` | query | number | 否 |
| `term` | query | number | 否 |
| `mortgageRequired` | query | string | 否 |
| `targetGroup` | query | string | 否 |
| `tagIds` | query | array<number> | 否 |
| `sortBy` | query | string | 否 |
| `pageNum` | query | number | 否 |
| `pageSize` | query | number | 否 |

**响应体**

`data` 为分页对象：`{ list: [产品卡片], total, pageNum, pageSize }`，产品卡片字段如下：

| 字段 | 类型 | 说明 |
|---|---|---|
| `productId` | 见联调 | — |
| `productName` | 见联调 | — |
| `institution` | 见联调 | — |
| `categoryCode` | 见联调 | — |
| `categoryName` | 见联调 | — |
| `coverImage` | 见联调 | — |
| `minAmount` | 见联调 | — |
| `maxAmount` | 见联调 | — |
| `minAnnualRate` | 见联调 | — |
| `maxAnnualRate` | 见联调 | — |
| `minTerm` | 见联调 | — |
| `maxTerm` | 见联调 | — |
| `termUnit` | 见联调 | — |
| `mortgageRequired` | 见联调 | — |
| `rateScope` | 见联调 | — |
| `tags` | 见联调 | — |

---

### 15. U6 产品对比

- **方法**：`GET`　**路径**：`/loan/products/compare`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `productIds` | query | array<number> | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `hint` | 见联调 |
| `list` | 见联调 |

---

### 16. U4 机构详情

- **方法**：`GET`　**路径**：`/loan/products/institutions/{institutionId}`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `institutionId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `introduction` | 见联调 |
| `products` | 见联调 |

---

### 17. U5 相似产品

- **方法**：`GET`　**路径**：`/loan/products/{productId}/similar`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `size` | query | number | 是 |
| `productId` | path | number | 是 |

**响应体**

`data` 为数组，元素类型 `object`。

---

### 18. 上报获客行为事件，支持批量

- **方法**：`POST`　**路径**：`/loan/products/events`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `batch` | body | ProductEventBatchRequest | 是 |

**请求体**（`application/json`，类型 `ProductEventBatchRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `events` | array<ProductEventRequest> | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `accepted` | 见联调 |
| `deduplicated` | 见联调 |

---

### 19. 确认推荐曝光，按 requestId 幂等

- **方法**：`POST`　**路径**：`/loan/products/impressions`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `impression` | body | ImpressionRequest | 是 |

**请求体**（`application/json`，类型 `ImpressionRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `requestId` | string | 否 | — | — |
| `scene` | string | 否 | — | — |
| `sessionId` | string | 否 | — | — |
| `source` | string | 否 | — | — |
| `abGroup` | string | 否 | — | — |
| `modelVersion` | string | 否 | — | — |
| `productIds` | array<number> | 否 | — | — |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `requestId` | 见联调 |

---

### 20. 四场景获客推荐

- **方法**：`GET`　**路径**：`/loan/products/recommendations`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `scene` | query | string | 是 |
| `productId` | query | number | 否 |
| `limit` | query | number | 否 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `abGroup` | 见联调 |
| `assessmentId` | 见联调 |
| `coldStart` | 见联调 |
| `fallbackUsed` | 见联调 |
| `items` | 见联调 |
| `missingProofMaterials` | 见联调 |
| `modelVersion` | 见联调 |
| `recallSources` | 见联调 |
| `recommendationReason` | 见联调 |
| `recommendationScore` | 见联调 |
| `requestId` | 见联调 |
| `riskCheckDegraded` | 见联调 |
| `scene` | 见联调 |

---

### 21. U8 成功页再推荐

- **方法**：`GET`　**路径**：`/loan/products/{productId}/post-loan-recommendations`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `productId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `abGroup` | 见联调 |
| `assessmentId` | 见联调 |
| `coldStart` | 见联调 |
| `emptyReason` | 见联调 |
| `emptyReasonCode` | 见联调 |
| `fallbackUsed` | 见联调 |
| `items` | 见联调 |
| `modelVersion` | 见联调 |
| `requestId` | 见联调 |
| `riskBlocked` | 见联调 |
| `scene` | 见联调 |
| `shouldRecommend` | 见联调 |

---

### 22. U3 产品详情

- **方法**：`GET`　**路径**：`/loan/products/{productId}`
- **鉴权**：需登录（角色 USER）
- **所属模块**：借贷产品目录（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `productId` | path | number | 是 |

**响应体**

`data` 为产品详情对象，字段如下：

| 字段 | 类型 | 说明 |
|---|---|---|
| `productId` | 见联调 | — |
| `productName` | 见联调 | — |
| `institution` | 见联调 | — |
| `categoryCode` | 见联调 | — |
| `categoryName` | 见联调 | — |
| `tags` | 见联调 | — |
| `summary` | 见联调 | — |
| `coverImage` | 见联调 | — |
| `detailImages` | 见联调 | — |
| `minAmount` | 见联调 | — |
| `maxAmount` | 见联调 | — |
| `minTerm` | 见联调 | — |
| `maxTerm` | 见联调 | — |
| `termUnit` | 见联调 | — |
| `minAnnualRate` | 见联调 | — |
| `maxAnnualRate` | 见联调 | — |
| `rateCalcMethod` | 见联调 | — |
| `rateScope` | 见联调 | — |
| `repaymentMethods` | 见联调 | — |
| `disbursementTime` | 见联调 | — |
| `targetGroups` | 见联调 | — |
| `minAge` | 见联调 | — |
| `maxAge` | 见联调 | — |
| `incomeRequirement` | 见联调 | — |
| `creditRequirement` | 见联调 | — |
| `occupationRequirement` | 见联调 | — |
| `mortgageRequired` | 见联调 | — |
| `regions` | 见联调 | — |
| `extraConditions` | 见联调 | — |
| `feeStatus` | 见联调 | — |
| `feeDescription` | 见联调 | — |
| `prepaymentDescription` | 见联调 | — |
| `remark` | 见联调 | — |
| `dataSource` | 见联调 | — |
| `verifiedAt` | 见联调 | — |
| `updateTime` | 见联调 | — |
| `riskTip` | 见联调 | — |
| `overview` | 见联调 | — |
| `loanInfo` | 见联调 | — |
| `conditions` | 见联调 | — |
| `materials` | 见联调 | — |
| `others` | 见联调 | — |
| `meta` | 见联调 | — |
| `inPlatformApply` | 见联调 | — |
| `status` | 见联调 | — |
| `everOnShelf` | 见联调 | — |
| `version` | 见联调 | — |
| `sortWeight` | 见联调 | — |

---

### 23. 查看还款计划

- **方法**：`GET`　**路径**：`/repayment/plans`
- **鉴权**：需登录（角色 USER）
- **所属模块**：账务与还款模块

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `createTime` | 见联调 |
| `currentPeriod` | 见联调 |
| `loanId` | 见联调 |
| `paidAmount` | 见联调 |
| `planId` | 见联调 |
| `remainingAmount` | 见联调 |
| `status` | 见联调 |
| `totalAmount` | 见联调 |
| `totalPeriods` | 见联调 |

---

### 24. 执行还款

- **方法**：`POST`　**路径**：`/repayment/execute`
- **鉴权**：需登录（角色 USER）
- **所属模块**：账务与还款模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `req` | body | RepaymentExecuteRequest | 是 |

**请求体**（`application/json`，类型 `RepaymentExecuteRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `planId` | number | 否 | 还款计划ID | 1 |
| `period` | number | 否 | 期数 | 1 |
| `amount` | number | 否 | 还款金额 | 1833.33 |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `recordId` | number | 否 | 还款记录ID | — |
| `planId` | number | 否 | 还款计划ID | — |
| `period` | number | 否 | 期数 | — |
| `actualAmount` | number | 否 | 实际还款金额 | — |
| `repaymentDate` | string(时间) | 否 | 还款日期 | — |
| `status` | string | 否 | 状态 | — |

---

### 25. 获取还款记录详情

- **方法**：`GET`　**路径**：`/repayment/record/{planId}`
- **鉴权**：需登录（角色 USER）
- **所属模块**：账务与还款模块

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `status` | query | string | 是 |
| `planId` | path | number | 是 |

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `actualAmount` | 见联调 |
| `amount` | 见联调 |
| `dueDate` | 见联调 |
| `interest` | 见联调 |
| `period` | 见联调 |
| `planId` | 见联调 |
| `principal` | 见联调 |
| `recordId` | 见联调 |
| `repaymentDate` | 见联调 |
| `status` | 见联调 |

---

### 26. 获取还款统计

- **方法**：`GET`　**路径**：`/repayment/statistics`
- **鉴权**：需权限点 collection:case
- **所属模块**：账务与还款模块

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `completedCount` | 见联调 |
| `totalCount` | 见联调 |

---

### 27. 获取逾期统计

- **方法**：`GET`　**路径**：`/repayment/overdue`
- **鉴权**：需权限点 collection:case
- **所属模块**：账务与还款模块

**响应体**

`data` 为 JSON 对象，字段如下（类型以实际联调为准）：

| 字段 | 类型 |
|---|---|
| `overdueCount` | 见联调 |
| `totalCount` | 见联调 |

---


## cs-service

### 28. status

- **方法**：`GET`　**路径**：`/api/v1/cs/status`
- **鉴权**：登录（未限角色）
- **所属模块**：CsStatusController

**响应体**

**注意**：本接口不走 `CommonResponse` 统一包裹，直接返回 JSON 对象：`{ "service": "cs-service", "status": "UP", "llmConnected": false }`。

---

### 29. AI 客服对话

- **方法**：`POST`　**路径**：`/api/v1/cs/chat`
- **鉴权**：需登录（角色 USER）
- **所属模块**：AI 客服（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | ChatRequest | 是 |

**请求体**（`application/json`，类型 `ChatRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `conversationId` | number | 否 | 会话ID，首次对话可不传 | — |
| `message` | string | 是 | 用户消息 | — |

**响应体**

`data` 对象字段如下：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `conversationId` | number | 否 | 会话ID | — |
| `reply` | string | 否 | 回复内容 | — |
| `intent` | string | 否 | 意图路由：PRODUCT/REALTIME/SCRIPT/TRANSFER | — |
| `model` | string | 否 | 生成回复的模型；fixed-script 表示规则话术，非大模型 | — |
| `status` | string | 否 | 会话状态：OPEN=AI接待中, TRANSFERRED=已转人工待接入, AGENT=人工接待中, CLOSED=已结束 | — |

---

### 30. 会话历史消息

- **方法**：`GET`　**路径**：`/api/v1/cs/conversations/{conversationId}/messages`
- **鉴权**：需登录（角色 USER）
- **所属模块**：AI 客服（用户端）

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

### 31. 提交转人工工单

- **方法**：`POST`　**路径**：`/api/v1/cs/tickets`
- **鉴权**：需登录（角色 USER）
- **所属模块**：转人工工单（用户端）

**请求参数**

| 参数 | 位置 | 类型 | 必填 |
|---|---|---|---|
| `request` | body | TicketCreateRequest | 是 |

**请求体**（`application/json`，类型 `TicketCreateRequest`）

| 字段 | 类型 | 必填 | 说明 | 示例 |
|---|---|---|---|---|
| `conversationId` | number | 否 | 来源会话ID（可选） | — |
| `category` | string | 是 | 问题分类：ACCOUNT/RISK/LOAN/REPAYMENT/OTHER | — |
| `description` | string | 是 | 问题描述 | — |
| `involvesMoney` | number | 是 | 是否涉及资金 0/1 | — |
| `priority` | string | 否 | 优先级：LOW/MEDIUM/HIGH（可留空，由系统按内容推断） | — |
| `bizId` | string | 否 | 关联业务ID（申请编号/借款编号等） | — |

**响应体**

`data` 对象字段：`ticketId`（工单编号，number）。

---

### 32. 我的工单列表

- **方法**：`GET`　**路径**：`/api/v1/cs/tickets`
- **鉴权**：需登录（角色 USER）
- **所属模块**：转人工工单（用户端）

**响应体**

`data` 为数组，元素字段如下：

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

