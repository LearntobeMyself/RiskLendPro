# Web 管理员端后端联调接口文档

本文档按当前 Web 管理员端页面整理，包含：

- 当前已经接入并可联调的接口。
- 当前页面仍使用 Mock 数据，但为了完整联调建议后端补齐的接口。
- 每个页面需要的请求参数、返回字段、状态枚举和操作接口。

## 1. 基础约定

### 1.1 前端请求前缀

前端 Axios 统一配置：

```txt
baseURL = /api
```

开发环境 Vite 代理：

```txt
/api -> http://47.109.109.231:8080/api/v1
```

所以前端代码请求：

```txt
/admin/login
```

后端实际需要提供：

```txt
/api/v1/admin/login
```

### 1.2 鉴权

除登录、注册外，其他管理员端接口均建议要求登录态。

前端登录后会携带：

```http
Authorization: Bearer <token>
```

token 来源：

```txt
POST /api/v1/admin/login -> data.token
```

### 1.3 通用响应格式

前端统一按下面结构判断业务是否成功：

```json
{
  "code": 200,
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

重要：

- HTTP 状态码是 200，但 `success: false`，前端仍认为失败。
- HTTP 状态码是 200，但 `code != 200`，前端仍认为失败。
- 错误信息请放在 `message`，前端会直接展示。

业务失败示例：

```json
{
  "code": 400,
  "success": false,
  "message": "不支持的还款方式：等额息金",
  "data": null
}
```

### 1.4 分页返回格式

列表接口建议统一返回：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [],
    "total": 0
  }
}
```

前端部分页面也兼容：

```json
{
  "data": {
    "records": [],
    "total": 0
  }
}
```

后端建议优先使用 `list + total`。

## 2. 页面与接口总览

| 页面 | 路由 | 当前状态 | 后端联调建议 |
|---|---|---|---|
| 登录 | `/login` | 已接入 | 必须联调 |
| 注册管理员 | `/login` 注册弹窗 | 已接入 | 必须联调 |
| 仪表盘 | `/dashboard` | 已接入 | 必须联调 |
| 贷款申请管理 | `/loan-application` | 已接入 | 必须联调 |
| 审批记录管理 | `/approval-records` | 本地记录 | 建议补接口 |
| 借款记录管理 | `/loan-records` | Mock | 建议补接口 |
| 还款记录管理 | `/repayment-records` | Mock | 建议补接口 |
| 所有用户还款计划 | `/admin-repayment-plans` | Mock fallback | 建议补接口 |
| 还款记录详情 | `/repayment-records-detail` | Mock fallback | 建议补接口 |
| 用户管理 | `/user-management` | Mock | 建议补接口 |
| 信用额度管理 | `/credit-limit` | Mock | 建议补接口 |
| 授信评估 | `/risk-assessment` | 已接入 | 必须联调 |
| 风控数据管理 | `/risk-management` | Mock | 建议补接口 |
| 数据分析 | `/data-analysis` | 已接入 | 必须联调 |
| 系统管理 | `/system-management` | Mock/占位 | 建议补接口 |

## 3. 管理员认证

### 3.1 管理员登录

```http
POST /api/v1/admin/login
```

请求体：

```json
{
  "username": "admin",
  "password": "123456"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "jwt-token",
    "id": 1,
    "username": "admin",
    "name": "管理员",
    "role": "admin"
  }
}
```

前端当前必用字段：

- `data.token`
- `data.id`

建议后端同时返回：

- `username`
- `name`
- `role`

### 3.2 管理员注册

```http
POST /api/v1/admin/register
```

请求体：

```json
{
  "username": "admin2",
  "password": "123456",
  "rePassword": "123456",
  "phoneNumber": "13800000000",
  "email": "admin2@example.com"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "注册成功",
  "data": null
}
```

### 3.3 当前管理员资料

当前前端未真实调用，但完整联调建议提供。

```http
GET /api/v1/admin/profile
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "id": 1,
    "username": "admin",
    "name": "管理员",
    "phoneNumber": "13800000000",
    "email": "admin@example.com",
    "role": "SUPER_ADMIN",
    "avatar": "",
    "lastLoginTime": "2026-06-27 10:00:00"
  }
}
```

## 4. 仪表盘 / 数据分析

### 4.1 管理端首页统计

页面：

- `/dashboard`
- `/data-analysis`

```http
GET /api/v1/admin/dashboard/stats
```

请求参数：无。

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "approvedToday": 3,
    "rejectedToday": 1,
    "pendingReview": 8,
    "totalApplications": 100,
    "totalDisbursed": 500000,
    "totalOverdue": 2,
    "overdueRate": 0.03
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| approvedToday | number | 今日审批通过数 |
| rejectedToday | number | 今日审批拒绝数 |
| pendingReview | number | 当前待审核数 |
| totalApplications | number | 累计申请数 |
| totalDisbursed | number | 累计放款金额 |
| totalOverdue | number | 当前逾期笔数 |
| overdueRate | number | 逾期率，0.03 表示 3% |

### 4.2 Vintage 分析

页面：`/data-analysis`

```http
GET /api/v1/admin/bi/vintage
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "vintageData": [
      {
        "month": "2026-01",
        "m1": 0.02,
        "m2": 0.04,
        "m3": 0.06
      }
    ]
  }
}
```

### 4.3 Roll-rate 分析

页面：`/data-analysis`

```http
GET /api/v1/admin/bi/roll-rate
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "currentStatus": "M0",
    "nextMonthStatus": {
      "M0": 0.85,
      "M1": 0.1,
      "M2": 0.03,
      "M3": 0.02
    }
  }
}
```

## 5. 贷款申请管理

页面：`/loan-application`

### 5.1 待审批贷款列表

当前已接入：

```http
GET /api/v1/admin/loan/pending-list
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页，从 1 开始 |
| size | number | 是 | 每页数量 |

前端筛选区还有以下字段，当前代码暂未传给后端，但完整联调建议支持：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | string | 否 | 申请状态 |
| applicant | string | 否 | 申请人姓名 |
| startDate | string | 否 | 申请开始日期，YYYY-MM-DD |
| endDate | string | 否 | 申请结束日期，YYYY-MM-DD |

推荐完整请求：

```http
GET /api/v1/admin/loan/pending-list?page=1&size=10&status=PENDING_APPROVAL&applicant=张三&startDate=2026-06-01&endDate=2026-06-27
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "loanId": 7,
        "userId": 1001,
        "userName": "许静雅",
        "phone": "13800000000",
        "idCard": "510106199903170001",
        "amount": 100000,
        "termMonths": 12,
        "purpose": "消费贷款",
        "interestRate": 0.05,
        "applyTime": "2026-06-27 10:00:00",
        "approvedTime": null,
        "updateTime": null,
        "status": "PENDING_APPROVAL",
        "totalScore": 82,
        "currentLimit": 50000,
        "exceedAmount": 100000,
        "repaymentMethod": "等额本金"
      }
    ],
    "total": 1
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| loanId | number/string | 贷款申请 ID，审批时作为 `loanId` 提交 |
| userId | number/string | 用户 ID |
| userName | string | 申请人姓名 |
| phone | string | 手机号 |
| idCard | string | 身份证号 |
| amount | number | 申请金额 |
| termMonths | number | 借款期限，单位月 |
| purpose | string | 借款用途 |
| interestRate | number | 年化利率，0.05 表示 5% |
| applyTime | string | 申请时间 |
| status | string | 申请状态 |
| totalScore | number | 风控评分 |
| currentLimit | number | 当前额度 |
| exceedAmount | number | 建议额度或超额金额 |
| repaymentMethod | string | 还款方式 |

状态枚举建议：

```txt
PENDING_APPROVAL
PENDING
APPROVED
REJECTED
OVERDUE
REPAID
```

还款方式必须避免错别字。建议只使用：

```txt
等额本金
等额本息
先息后本
```

不要返回：

```txt
等额息金
```

### 5.2 贷款申请详情

当前详情弹窗主要使用列表行数据，完整联调建议提供：

```http
GET /api/v1/admin/loan/applications/{loanId}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "loanId": 7,
    "userId": 1001,
    "userName": "许静雅",
    "phone": "13800000000",
    "idCard": "510106199903170001",
    "amount": 100000,
    "termMonths": 12,
    "purpose": "消费贷款",
    "interestRate": 0.05,
    "repaymentMethod": "等额本金",
    "status": "PENDING_APPROVAL",
    "applyTime": "2026-06-27 10:00:00",
    "creditScore": 82,
    "currentLimit": 50000,
    "exceedAmount": 100000,
    "riskReportId": "RISK202606270001",
    "materials": []
  }
}
```

### 5.3 贷款审批通过

当前已接入：

```http
POST /api/v1/admin/loan/approve
```

请求体：

```json
{
  "loanId": 7,
  "approveResult": "APPROVE",
  "additionalLimit": 100000,
  "repaymentMethod": "等额本金",
  "approveRemark": "审批通过"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "审批成功",
  "data": {
    "loanId": 7,
    "userId": 1001,
    "status": "APPROVED",
    "approveTime": "2026-06-27 10:30:00"
  }
}
```

### 5.4 贷款审批拒绝

当前已接入：

```http
POST /api/v1/admin/loan/approve
```

请求体：

```json
{
  "loanId": 7,
  "approveResult": "REJECT",
  "approveRemark": "资料不完整，拒绝通过"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "拒绝成功",
  "data": {
    "loanId": 7,
    "userId": 1001,
    "status": "REJECTED",
    "approveTime": "2026-06-27 10:35:00"
  }
}
```

### 5.5 批量审批

当前前端逐条循环调用 `POST /admin/loan/approve`。完整联调建议提供批量接口：

```http
POST /api/v1/admin/loan/batch-approve
```

请求体：

```json
{
  "loanIds": [7, 8, 9],
  "approveResult": "APPROVE",
  "approveRemark": "批量审批通过"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "批量审批完成",
  "data": {
    "successCount": 3,
    "failCount": 0,
    "failedItems": []
  }
}
```

## 6. 审批记录管理

页面：`/approval-records`

当前前端使用本地 `localStorage` 保存审批记录。为了完整联调建议后端提供。

### 6.1 审批记录列表

```http
GET /api/v1/admin/loan/approval-records
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| userId | string | 否 | 用户 ID |
| loanId | string | 否 | 贷款 ID |
| status | string | 否 | `APPROVED` / `REJECTED` |
| startDate | string | 否 | 审批开始日期 |
| endDate | string | 否 | 审批结束日期 |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": "7-20260627103000",
        "loanId": 7,
        "userId": 1001,
        "reviewerId": 1,
        "reviewerName": "admin",
        "applicantName": "许静雅",
        "status": "APPROVED",
        "comment": "审批通过",
        "createTime": "2026-06-27 10:30:00",
        "updateTime": "2026-06-27 10:30:00"
      }
    ],
    "total": 1
  }
}
```

## 7. 借款记录管理

页面：`/loan-records`

当前页面是 Mock 数据，完整联调建议提供以下接口。

### 7.1 借款记录列表

```http
GET /api/v1/admin/loan/records
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| status | string | 否 | 借款状态 |
| userName | string | 否 | 用户姓名 |
| startDate | string | 否 | 借款开始日期 |
| endDate | string | 否 | 借款结束日期 |

状态枚举：

```txt
REPAYING
SETTLED
OVERDUE
BAD_DEBT
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "loanId": 20230001,
        "userId": 1001,
        "userName": "张三",
        "phone": "13800000000",
        "loanAmount": 30000,
        "loanTerm": 12,
        "interestRate": 5.5,
        "loanTime": "2026-01-15 10:00:00",
        "dueTime": "2027-01-15 10:00:00",
        "paidAmount": 5000,
        "status": "REPAYING",
        "overdueDays": 0,
        "avatar": ""
      }
    ],
    "total": 1
  }
}
```

### 7.2 借款详情

```http
GET /api/v1/admin/loan/records/{loanId}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "loanId": 20230001,
    "userId": 1001,
    "userName": "张三",
    "phone": "13800000000",
    "idCard": "510106199903170001",
    "loanAmount": 30000,
    "loanTerm": 12,
    "interestRate": 5.5,
    "repaymentMethod": "等额本金",
    "loanTime": "2026-01-15 10:00:00",
    "dueTime": "2027-01-15 10:00:00",
    "paidAmount": 5000,
    "remainingAmount": 25000,
    "status": "REPAYING",
    "overdueDays": 0
  }
}
```

### 7.3 借款记录导出

```http
GET /api/v1/admin/loan/records/export
```

请求参数同列表接口。

响应建议返回文件流，或返回下载地址：

```json
{
  "code": 200,
  "success": true,
  "message": "导出成功",
  "data": {
    "downloadUrl": "https://example.com/files/loan-records.xlsx"
  }
}
```

## 8. 还款管理

### 8.1 还款记录管理汇总页

页面：`/repayment-records`

当前 Mock 页面包含：

- 还款统计卡片。
- 还款计划 tab。
- 实际还款记录 tab。
- 逾期记录 tab。
- 生成报表按钮。
- 逾期分析弹窗。

#### 8.1.1 还款统计

```http
GET /api/v1/admin/repayment/summary
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "receivableThisMonth": 1245680,
    "receivedThisMonth": 983450,
    "pendingAmount": 156230,
    "overdueAmount": 42780
  }
}
```

#### 8.1.2 还款计划列表

```http
GET /api/v1/admin/repayment/plans
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| loanId | string | 否 | 贷款 ID |
| userName | string | 否 | 用户姓名 |
| status | string | 否 | 状态 |

成功响应字段参考 8.2。

#### 8.1.3 实际还款记录列表

```http
GET /api/v1/admin/repayment/actual-records
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| loanId | string | 否 | 贷款 ID |
| userName | string | 否 | 用户姓名 |
| startDate | string | 否 | 还款开始日期 |
| endDate | string | 否 | 还款结束日期 |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1,
        "loanId": 20230001,
        "userName": "张三",
        "repayDate": "2026-06-20",
        "repayAmount": 3000,
        "repayType": "银行卡",
        "status": "SUCCESS",
        "operator": "系统自动"
      }
    ],
    "total": 1
  }
}
```

#### 8.1.4 逾期记录列表

```http
GET /api/v1/admin/repayment/overdue-records
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1,
        "loanId": 20230001,
        "userName": "张三",
        "overdueAmount": 2000,
        "overdueDays": 12,
        "overdueDate": "2026-06-15",
        "contactTimes": 2,
        "lastContact": "2026-06-26",
        "status": "COLLECTING"
      }
    ],
    "total": 1
  }
}
```

#### 8.1.5 生成还款报表

```http
POST /api/v1/admin/repayment/report
```

请求体：

```json
{
  "startDate": "2026-06-01",
  "endDate": "2026-06-27",
  "type": "MONTHLY"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "报表生成成功",
  "data": {
    "reportId": "RPT202606270001",
    "downloadUrl": "https://example.com/files/repayment-report.xlsx"
  }
}
```

### 8.2 所有用户还款计划

页面：`/admin-repayment-plans`

当前 API 封装名：`getAdminRepaymentPlans`，目前 Mock fallback。建议后端提供：

```http
GET /api/v1/admin/repayment/plans
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| loanId | string | 否 | 贷款 ID |
| status | string | 否 | `ACTIVE` / `COMPLETED` / `PENDING` / `OVERDUE` / `CANCELLED` |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "获取所有用户还款计划成功",
  "data": {
    "list": [
      {
        "planId": 2034921324534972417,
        "loanId": 2034921323628728320,
        "userId": 1001,
        "userName": "张三",
        "totalAmount": 12400.00,
        "principalAmount": 12000.00,
        "interestAmount": 400.00,
        "repaymentMethod": "等额本金",
        "totalPeriods": 15,
        "currentPeriod": 4,
        "status": "ACTIVE",
        "statusDesc": "还款中",
        "startDate": "2026-04-20",
        "endDate": "2027-06-20",
        "remainingAmount": 12400.00,
        "overdueFeeRate": 0.00050,
        "paidPeriods": 0,
        "paidAmount": 0,
        "progressPercentage": 0.0,
        "createTime": "2026-03-20 17:13:45",
        "updateTime": "2026-03-20 17:13:45"
      }
    ],
    "total": 1
  }
}
```

### 8.3 还款计划详情

```http
GET /api/v1/admin/repayment/plans/{planId}
```

返回字段同 8.2 单条记录，可额外包含：

```json
{
  "repaymentRecords": [],
  "overdueRecords": [],
  "completedRecords": [],
  "activeRecords": []
}
```

### 8.4 逾期还款计划统计

页面：`/admin-repayment-plans`

```http
GET /api/v1/admin/repayment/overdue-stats
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "totalCount": 75,
    "overdueCount": 6
  }
}
```

### 8.5 还款记录详情列表

页面：`/repayment-records-detail`

```http
GET /api/v1/admin/repayment/plans/{planId}/records
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| status | string | 否 | 状态 |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "获取还款记录成功",
  "data": {
    "list": [
      {
        "recordId": 2034921324958597122,
        "planId": 2034921324534972417,
        "period": 1,
        "amount": 850.00,
        "principal": 800.00,
        "interest": 50.00,
        "actualAmount": 850.00,
        "dueDate": "2026-04-20",
        "repaymentDate": "2026-03-27 15:47:45",
        "status": "COMPLETED",
        "overdueDays": 0,
        "overdueFee": 0.00,
        "createTime": "2026-03-20 17:13:46",
        "updateTime": "2026-03-20 17:13:46"
      }
    ],
    "total": 1
  }
}
```

### 8.6 还款记录统计

页面：`/repayment-records-detail`

```http
GET /api/v1/admin/repayment/records/stats
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| planId | string | 否 | 计划 ID |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "totalCount": 75,
    "completedCount": 3
  }
}
```

### 8.7 手动执行还款

当前 API 封装中已有，但管理员端页面暂未主流程使用。

```http
POST /api/v1/repayment/execute
```

请求体：

```json
{
  "planId": 2034921324534972417,
  "period": 4,
  "amount": 840.00
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "还款成功",
  "data": {
    "recordId": 2034921326183333889,
    "planId": 2034921324534972417,
    "period": 4,
    "status": "COMPLETED",
    "repaymentDate": "2026-06-27 12:00:00"
  }
}
```

### 8.8 发送还款提醒

页面：`/user-management` 的“发送提醒”按钮也会用到。

```http
POST /api/v1/admin/repayment/reminders
```

请求体：

```json
{
  "userId": 1001,
  "loanId": 20230001,
  "planId": 2034921324534972417,
  "channel": "SMS",
  "message": "您有一笔还款即将到期，请及时处理"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "提醒发送成功",
  "data": {
    "reminderId": "REM202606270001",
    "sendTime": "2026-06-27 12:00:00"
  }
}
```

## 9. 用户管理

页面：`/user-management`

当前 Mock 页面，建议补齐。

### 9.1 用户列表

```http
GET /api/v1/admin/users
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| status | string | 否 | `ACTIVE` / `DISABLED` / `FROZEN` |
| name | string | 否 | 用户姓名 |
| phone | string | 否 | 手机号 |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1001,
        "name": "张三",
        "phone": "13800000000",
        "idCard": "510***********0001",
        "creditScore": 720,
        "creditLimit": 50000,
        "loanCount": 3,
        "totalLoan": 120000,
        "status": "ACTIVE",
        "registerTime": "2026-01-01 10:00:00",
        "avatar": ""
      }
    ],
    "total": 1
  }
}
```

### 9.2 用户详情

```http
GET /api/v1/admin/users/{userId}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "id": 1001,
    "name": "张三",
    "phone": "13800000000",
    "email": "zhangsan@example.com",
    "idCard": "510106199903170001",
    "creditScore": 720,
    "creditLimit": 50000,
    "loanCount": 3,
    "totalLoan": 120000,
    "status": "ACTIVE",
    "registerTime": "2026-01-01 10:00:00",
    "lastLoginTime": "2026-06-27 09:00:00"
  }
}
```

### 9.3 新增用户

```http
POST /api/v1/admin/users
```

请求体：

```json
{
  "name": "张三",
  "phone": "13800000000",
  "email": "zhangsan@example.com",
  "idCard": "510106199903170001",
  "status": "ACTIVE"
}
```

### 9.4 编辑用户

```http
PUT /api/v1/admin/users/{userId}
```

请求体同新增用户，可按需局部更新。

### 9.5 启用/禁用/冻结用户

```http
PATCH /api/v1/admin/users/{userId}/status
```

请求体：

```json
{
  "status": "DISABLED",
  "reason": "管理员手动禁用"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "状态更新成功",
  "data": {
    "userId": 1001,
    "status": "DISABLED"
  }
}
```

### 9.6 用户导出

```http
GET /api/v1/admin/users/export
```

请求参数同用户列表。

## 10. 信用额度管理

页面：`/credit-limit`

当前 Mock 页面，建议补齐。

### 10.1 额度统计

```http
GET /api/v1/admin/credit/stats
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "totalCreditLimit": 5280000,
    "usedCreditLimit": 2150000,
    "availableCreditLimit": 3130000,
    "averageUsageRate": 40.7,
    "trends": {
      "totalCreditLimit": "+8.5%",
      "usedCreditLimit": "+12.3%",
      "availableCreditLimit": "-3.8%",
      "averageUsageRate": "+2.1%"
    }
  }
}
```

### 10.2 额度列表

```http
GET /api/v1/admin/credit/limits
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| userName | string | 否 | 用户姓名 |
| phone | string | 否 | 手机号 |
| status | string | 否 | `NORMAL` / `FROZEN` / `OVERLIMIT` |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "userId": 1001,
        "userName": "张三",
        "phone": "13800000000",
        "idCard": "510***********0001",
        "creditScore": 720,
        "totalLimit": 50000,
        "usedLimit": 12000,
        "availableLimit": 38000,
        "usageRate": 24,
        "status": "NORMAL",
        "lastAdjustTime": "2026-06-20 10:00:00",
        "avatar": ""
      }
    ],
    "total": 1
  }
}
```

### 10.3 手动调整额度

```http
POST /api/v1/admin/credit/limits/{userId}/adjust
```

请求体：

```json
{
  "newLimit": 80000,
  "reason": "用户资质提升，手动调额",
  "remark": "管理员调整"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "额度调整成功",
  "data": {
    "userId": 1001,
    "oldLimit": 50000,
    "newLimit": 80000,
    "adjustTime": "2026-06-27 12:00:00"
  }
}
```

### 10.4 批量调整额度

```http
POST /api/v1/admin/credit/limits/batch-adjust
```

请求体：

```json
{
  "userIds": [1001, 1002],
  "adjustType": "PERCENT",
  "adjustValue": 10,
  "reason": "批量调额"
}
```

说明：

- `adjustType = FIXED` 表示设置为固定额度。
- `adjustType = INCREASE` 表示增加固定金额。
- `adjustType = DECREASE` 表示减少固定金额。
- `adjustType = PERCENT` 表示按比例调整。

### 10.5 额度调整记录

```http
GET /api/v1/admin/credit/limits/{userId}/history
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1,
        "userId": 1001,
        "oldLimit": 50000,
        "newLimit": 80000,
        "reason": "用户资质提升",
        "operatorId": 1,
        "operatorName": "admin",
        "adjustTime": "2026-06-27 12:00:00"
      }
    ],
    "total": 1
  }
}
```

### 10.6 逾期自动调额规则

页面展示了 M1-M4 规则，建议后端提供：

```http
GET /api/v1/admin/credit/overdue-adjust-rules
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": [
    {
      "level": "N",
      "minDays": 0,
      "maxDays": 0,
      "retainRatio": 1.0,
      "reason": "正常状态"
    },
    {
      "level": "M1",
      "minDays": 1,
      "maxDays": 30,
      "retainRatio": 0.8,
      "reason": "轻度逾期，风险可控"
    },
    {
      "level": "M2",
      "minDays": 31,
      "maxDays": 60,
      "retainRatio": 0.5,
      "reason": "中度逾期，风险较高"
    },
    {
      "level": "M3",
      "minDays": 61,
      "maxDays": 90,
      "retainRatio": 0.2,
      "reason": "重度逾期，风险严重"
    },
    {
      "level": "M4",
      "minDays": 91,
      "maxDays": null,
      "retainRatio": 0,
      "reason": "严重逾期，额度清零"
    }
  ]
}
```

## 11. 授信评估 / 风控审批

页面：`/risk-assessment`

### 11.1 提交风控评估

当前已接入：

```http
POST /api/v1/risk/assessment/submit
```

请求体：

```json
{
  "idCard": "510106199903170001",
  "name": "张三",
  "phone": "13800008000",
  "email": "zhangsan@example.com",
  "gender": 1,
  "birthday": "1999-03-17",
  "education": "本科",
  "marriage": "单身",
  "jobType": "私营企业",
  "monthlyIncome": "8000-15000",
  "hasHouse": false,
  "hasCar": false,
  "contactPhone": "13900009000"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "申请已受理，风控评估启动",
  "data": {
    "applyId": "RISK202606270001",
    "status": "WAITING",
    "isFinal": false
  }
}
```

### 11.2 查询风控状态

当前已接入：

```http
GET /api/v1/risk/assessment/status
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| applyId | string | 是 | 风控申请 ID |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "applyId": "RISK202606270001",
    "status": "MANUAL_REVIEW",
    "totalScore": 68,
    "sysDecision": "REVIEW",
    "isFinal": false
  }
}
```

### 11.3 查询风控最终结果

当前已接入：

```http
GET /api/v1/risk/assessment/result
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| applyId | string | 是 | 风控申请 ID |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "applyId": "RISK202606270001",
    "status": "FINAL_PASS",
    "creditLimit": 30000,
    "expireDate": "2027-06-27",
    "approvalTime": "2026-06-27 11:00:00"
  }
}
```

### 11.4 人工复核列表

当前已接入：

```http
GET /api/v1/admin/risk/list
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| status | string | 否 | 状态 |

状态枚举：

```txt
WAITING
SYSTEM_REJECT
MANUAL_REVIEW
FINAL_PASS
FINAL_REJECT
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "applyId": "RISK202606270001",
        "userName": "张三",
        "phone": "13800008000",
        "idCard": "510106199903170001",
        "totalScore": 68,
        "sysDecision": "REVIEW",
        "status": "MANUAL_REVIEW",
        "applyTime": "2026-06-27 10:00:00"
      }
    ],
    "total": 1
  }
}
```

### 11.5 风控报告详情

当前已接入：

```http
GET /api/v1/admin/risk/report/{applyId}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "applyId": "RISK202606270001",
    "totalScore": 68,
    "systemDecision": "REVIEW",
    "suggestedAmount": 30000,
    "blacklistCheck": {
      "hit": false
    },
    "userDetails": {
      "name": "张三",
      "age": 27,
      "education": "本科",
      "jobType": "私营企业",
      "monthlyIncome": "8000-15000",
      "hasHouse": false,
      "hasCar": false
    },
    "scoreDetails": [
      {
        "feature": "收入水平",
        "value": "8000-15000",
        "weight": 20,
        "contribution": 12,
        "description": "收入稳定"
      }
    ]
  }
}
```

### 11.6 风控人工审批

当前已接入：

```http
POST /api/v1/admin/risk/approve
```

通过请求体：

```json
{
  "applyId": "RISK202606270001",
  "auditResult": "PASS",
  "creditLimit": 30000,
  "auditRemark": "综合评分达标，同意授信"
}
```

拒绝请求体：

```json
{
  "applyId": "RISK202606270001",
  "auditResult": "REJECT",
  "auditRemark": "风险特征较多，暂不通过"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "审批成功",
  "data": {
    "applyId": "RISK202606270001",
    "status": "FINAL_PASS",
    "approvalTime": "2026-06-27 11:20:00"
  }
}
```

## 12. 风控数据管理

页面：`/risk-management`

当前 Mock 页面，建议补齐。

### 12.1 风险概览

```http
GET /api/v1/admin/risk/overview
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "monitoredUserCount": 2847,
    "lowRiskRate": 92.5,
    "mediumRiskRate": 5.8,
    "highRiskRate": 1.7,
    "trends": {
      "monitoredUserCount": "+5.2%",
      "lowRiskRate": "+1.3%",
      "mediumRiskRate": "-0.7%",
      "highRiskRate": "-0.6%"
    }
  }
}
```

### 12.2 用户信用评分列表

```http
GET /api/v1/admin/risk/credit-scores
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| riskLevel | string | 否 | `LOW` / `MEDIUM` / `HIGH` |
| userName | string | 否 | 用户姓名 |
| phone | string | 否 | 手机号 |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "userId": 1001,
        "userName": "张三",
        "phone": "13800000000",
        "creditScore": 720,
        "riskLevel": "LOW",
        "riskLevelDesc": "低风险",
        "lastUpdate": "2026-06-27 10:00:00"
      }
    ],
    "total": 1
  }
}
```

### 12.3 用户风控详情

```http
GET /api/v1/admin/risk/users/{userId}
```

成功响应建议包含信用评分、反欺诈、多头借贷、征信摘要。

### 12.4 反欺诈检测列表

```http
GET /api/v1/admin/risk/anti-fraud
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1,
        "userId": 1001,
        "userName": "张三",
        "detectTime": "2026-06-27 10:00:00",
        "riskType": "DEVICE_ABNORMAL",
        "riskTypeDesc": "设备异常",
        "confidence": 86,
        "status": "PENDING"
      }
    ],
    "total": 1
  }
}
```

### 12.5 处理反欺诈告警

```http
POST /api/v1/admin/risk/anti-fraud/{id}/handle
```

请求体：

```json
{
  "handleResult": "CONFIRMED",
  "remark": "确认异常，已限制账户"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "处理成功",
  "data": {
    "id": 1,
    "status": "HANDLED",
    "handleTime": "2026-06-27 12:00:00"
  }
}
```

### 12.6 多头借贷列表

```http
GET /api/v1/admin/risk/multi-loan
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "userId": 1001,
        "userName": "张三",
        "idCard": "510***********0001",
        "platformCount": 5,
        "totalAmount": 120000,
        "queryTime": "2026-06-27 10:00:00"
      }
    ],
    "total": 1
  }
}
```

### 12.7 征信报告摘要列表

```http
GET /api/v1/admin/risk/credit-reports
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "userId": 1001,
        "userName": "张三",
        "queryTime": "2026-06-27 10:00:00",
        "creditScore": 720,
        "overdueCount": 0,
        "loanCount": 3,
        "creditCardCount": 2,
        "queryCount": 4,
        "summary": "信用记录良好，无重大逾期记录"
      }
    ],
    "total": 1
  }
}
```

### 12.8 完整征信报告

```http
GET /api/v1/admin/risk/credit-reports/{userId}
```

成功响应根据后端征信模型返回，建议包含：

- 基本信息。
- 信贷记录。
- 逾期记录。
- 查询记录。
- 风险摘要。

### 12.9 导出风控报告

```http
GET /api/v1/admin/risk/export
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| type | string | 否 | `CREDIT_SCORE` / `ANTI_FRAUD` / `MULTI_LOAN` / `CREDIT_REPORT` |
| startDate | string | 否 | 开始日期 |
| endDate | string | 否 | 结束日期 |

## 13. 系统管理

页面：`/system-management`

当前子组件仍是占位，但页面需要完整联调可按以下接口补齐。

### 13.1 管理员列表

```http
GET /api/v1/admin/system/admins
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| username | string | 否 | 账号 |
| status | string | 否 | 状态 |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1,
        "username": "admin",
        "name": "超级管理员",
        "phoneNumber": "13800000000",
        "email": "admin@example.com",
        "role": "SUPER_ADMIN",
        "status": "ACTIVE",
        "createTime": "2026-01-01 10:00:00",
        "lastLoginTime": "2026-06-27 10:00:00"
      }
    ],
    "total": 1
  }
}
```

### 13.2 新增管理员

```http
POST /api/v1/admin/system/admins
```

请求体：

```json
{
  "username": "risk_admin",
  "password": "123456",
  "name": "风控管理员",
  "phoneNumber": "13800000001",
  "email": "risk@example.com",
  "role": "RISK_ADMIN"
}
```

### 13.3 编辑管理员

```http
PUT /api/v1/admin/system/admins/{adminId}
```

请求体：

```json
{
  "name": "风控管理员",
  "phoneNumber": "13800000001",
  "email": "risk@example.com",
  "role": "RISK_ADMIN",
  "status": "ACTIVE"
}
```

### 13.4 删除管理员

```http
DELETE /api/v1/admin/system/admins/{adminId}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "删除成功",
  "data": null
}
```

### 13.5 操作日志

```http
GET /api/v1/admin/system/operation-logs
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | number | 是 | 当前页 |
| size | number | 是 | 每页数量 |
| operatorName | string | 否 | 操作人 |
| module | string | 否 | 模块 |
| action | string | 否 | 操作类型 |
| startTime | string | 否 | 开始时间 |
| endTime | string | 否 | 结束时间 |

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "id": 1,
        "operatorId": 1,
        "operatorName": "admin",
        "module": "贷款审批",
        "action": "APPROVE_LOAN",
        "description": "审批通过贷款申请 7",
        "ip": "127.0.0.1",
        "result": "SUCCESS",
        "createTime": "2026-06-27 10:30:00"
      }
    ],
    "total": 1
  }
}
```

### 13.6 系统配置查询

```http
GET /api/v1/admin/system/config
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "loanMinAmount": 1000,
    "loanMaxAmount": 100000,
    "defaultInterestRate": 0.05,
    "maxLoanTermMonths": 36,
    "overdueFeeRate": 0.0005,
    "riskManualReviewScoreMin": 50,
    "riskManualReviewScoreMax": 70,
    "autoApproveScore": 75,
    "autoRejectScore": 40
  }
}
```

### 13.7 系统配置更新

```http
PUT /api/v1/admin/system/config
```

请求体同 13.6 的 `data`。

### 13.8 数据备份列表

```http
GET /api/v1/admin/system/backups
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "查询成功",
  "data": {
    "list": [
      {
        "backupId": "BAK202606270001",
        "fileName": "backup-20260627.sql",
        "fileSize": 2048000,
        "status": "SUCCESS",
        "createTime": "2026-06-27 02:00:00",
        "operatorName": "system"
      }
    ],
    "total": 1
  }
}
```

### 13.9 创建数据备份

```http
POST /api/v1/admin/system/backups
```

请求体：

```json
{
  "backupType": "FULL",
  "remark": "手动备份"
}
```

成功响应：

```json
{
  "code": 200,
  "success": true,
  "message": "备份任务已创建",
  "data": {
    "backupId": "BAK202606270001",
    "status": "RUNNING"
  }
}
```

### 13.10 恢复数据备份

```http
POST /api/v1/admin/system/backups/{backupId}/restore
```

请求体：

```json
{
  "confirm": true,
  "remark": "确认恢复到该备份"
}
```

### 13.11 下载备份

```http
GET /api/v1/admin/system/backups/{backupId}/download
```

响应可以是文件流，也可以返回下载地址。

## 14. 状态枚举建议

### 14.1 贷款状态

```txt
PENDING_APPROVAL  待审批
APPROVED          已通过
REJECTED          已拒绝
REPAYING          还款中
SETTLED           已结清
OVERDUE           逾期
BAD_DEBT          坏账
```

### 14.2 还款计划状态

```txt
PENDING    待还款
ACTIVE     还款中
COMPLETED  已完成
OVERDUE    逾期
CANCELLED  已取消
```

### 14.3 用户状态

```txt
ACTIVE    正常
DISABLED  禁用
FROZEN    冻结
```

### 14.4 额度状态

```txt
NORMAL     正常
FROZEN     冻结
OVERLIMIT  超额
```

### 14.5 风控状态

```txt
WAITING        待处理
SYSTEM_REJECT  系统拒绝
MANUAL_REVIEW  人工复核
FINAL_PASS     最终通过
FINAL_REJECT   最终拒绝
```

### 14.6 风控系统建议

```txt
APPROVE        建议通过
REVIEW         建议复核
REJECT         建议拒绝
MANUAL_REVIEW  人工复核
```

### 14.7 审批动作

贷款审批：

```txt
APPROVE
REJECT
```

风控人工审批：

```txt
PASS
REJECT
```

## 15. 本轮联调优先级

### P0：已经接入，必须优先保证成功

```txt
POST /api/v1/admin/login
POST /api/v1/admin/register
GET  /api/v1/admin/dashboard/stats
GET  /api/v1/admin/loan/pending-list
POST /api/v1/admin/loan/approve
POST /api/v1/risk/assessment/submit
GET  /api/v1/risk/assessment/status
GET  /api/v1/risk/assessment/result
GET  /api/v1/admin/risk/list
GET  /api/v1/admin/risk/report/{applyId}
POST /api/v1/admin/risk/approve
GET  /api/v1/admin/bi/vintage
GET  /api/v1/admin/bi/roll-rate
```

### P1：当前 Mock 页面，建议下一步补齐并联调

```txt
GET  /api/v1/admin/loan/approval-records
GET  /api/v1/admin/loan/records
GET  /api/v1/admin/loan/records/{loanId}
GET  /api/v1/admin/repayment/summary
GET  /api/v1/admin/repayment/plans
GET  /api/v1/admin/repayment/plans/{planId}
GET  /api/v1/admin/repayment/overdue-stats
GET  /api/v1/admin/repayment/plans/{planId}/records
GET  /api/v1/admin/repayment/records/stats
POST /api/v1/admin/repayment/reminders
GET  /api/v1/admin/users
GET  /api/v1/admin/users/{userId}
PATCH /api/v1/admin/users/{userId}/status
GET  /api/v1/admin/credit/stats
GET  /api/v1/admin/credit/limits
POST /api/v1/admin/credit/limits/{userId}/adjust
GET  /api/v1/admin/credit/limits/{userId}/history
GET  /api/v1/admin/risk/overview
GET  /api/v1/admin/risk/credit-scores
GET  /api/v1/admin/risk/anti-fraud
POST /api/v1/admin/risk/anti-fraud/{id}/handle
GET  /api/v1/admin/risk/multi-loan
GET  /api/v1/admin/risk/credit-reports
```

### P2：系统管理与导出类

```txt
GET    /api/v1/admin/system/admins
POST   /api/v1/admin/system/admins
PUT    /api/v1/admin/system/admins/{adminId}
DELETE /api/v1/admin/system/admins/{adminId}
GET    /api/v1/admin/system/operation-logs
GET    /api/v1/admin/system/config
PUT    /api/v1/admin/system/config
GET    /api/v1/admin/system/backups
POST   /api/v1/admin/system/backups
POST   /api/v1/admin/system/backups/{backupId}/restore
GET    /api/v1/admin/system/backups/{backupId}/download
GET    /api/v1/admin/loan/records/export
GET    /api/v1/admin/users/export
GET    /api/v1/admin/risk/export
POST   /api/v1/admin/repayment/report
```

## 16. 常见联调问题

1. HTTP 200 不代表前端成功。必须 `code: 200` 且 `success: true`。
2. 登录必须返回 `data.token`。
3. 列表接口必须返回 `total`，否则分页总数会不正确。
4. 贷款审批接口必须能识别 `approveResult: APPROVE/REJECT`。
5. 还款方式不要使用 `等额息金`，建议统一为 `等额本金`。
6. 日期字段建议统一返回字符串：`YYYY-MM-DD HH:mm:ss`。
7. 金额字段返回 number，不要返回带 `￥`、`,` 的字符串。
8. 百分比建议返回小数或数值时保持一致。例如 `overdueRate: 0.03` 表示 3%，`usageRate: 24` 表示 24%。具体按本文各接口示例。
