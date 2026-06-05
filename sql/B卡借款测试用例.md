# B 卡借款专项测试用例（独立角色）

> 与 [`测试用例.md`](测试用例.md) 中吴小微/赵六/孙七等 **完全独立**：新手机号 `13800138101–13800138104`。
>
> 每个用例均提供 **可直接复制** 的注册 / 登录 / 风险评估 JSON。Swagger / Postman 均可。

---

## 0. 环境与前置（一次性）

```text
1. 主库 risklendpro 已执行 sql/migration_b_card.sql
2. credit_data_db 已 load_to_mysql 步骤6（B 卡规则 + user_behavior_features）
3. Spring Boot 已重启
```

**API 前缀**：`http://localhost:8080/api/v1`  
**用户密码**：`Test123456`  
**A 卡阈值**：≥788 自动通过；642–787 人工审核；<642 拒绝

### 0.1 管理员登录 `POST /admin/login`

```json
{
  "username": "admin",
  "password": "Admin123456"
}
```

Header：`Authorization: Bearer <管理员 token>`

---

## 用例一览

| 编号 | 角色 | 手机 | 测什么 |
|------|------|------|--------|
| B-L01 | 陈慧清 | 13800138101 | A 卡自动通过 → 首次借款 → B 卡 activate |
| B-L02 | 韩立成 | 13800138102 | A 卡自动通过 → **两次借款**（不重跑 A 卡） |
| B-L03 | 许静雅 | 13800138103 | A 卡人工审核 → 额度外借款 → 管理员批贷后 B 卡启用 |
| B-L04 | 罗明远 | 13800138104 | A 卡自动通过 → 借款 → seed 逾期 → B 分下降 |

| 角色 | sk_id_curr | id_card | 离线分约* |
|------|------------|---------|-----------|
| 陈慧清 | 140869 | 110112197410240031 | 850+ APPROVE |
| 韩立成 | 112899 | 110112197204080068 | 820+ APPROVE |
| 许静雅 | 116151 | 110112198203210013 | 750 左右 MANUAL_REVIEW |
| 罗明远 | 142662 | 110112197905220248 | 880+ APPROVE |

\* `python score_demo_users.py` 可校准；线上允许 ±2 分。

---

## B-L01 陈慧清 — 首次放款启用 B 卡

### 1）注册 `POST /auth/register`

```json
{
  "realName": "陈慧清",
  "phoneNumber": "13800138101",
  "email": "chen_huiqing@example.com",
  "idCard": "110112197410240031",
  "password": "Test123456",
  "repassword": "Test123456"
}
```

### 2）登录 `POST /auth/login`

```json
{
  "phoneNumber": "13800138101",
  "password": "Test123456"
}
```

Header：`Authorization: Bearer <用户 token>`

### 3）风险评估 `POST /risk/submit`

```json
{
  "idCard": "110112197410240031",
  "name": "陈慧清",
  "phone": "13800138101",
  "email": "chen_huiqing@example.com",
  "gender": 0,
  "birthday": "1974-10-24",
  "education": "本科",
  "marriage": "已婚",
  "jobType": "企事业单位",
  "monthlyIncome": "15000以上",
  "hasHouse": true,
  "hasCar": true,
  "contactPhone": "13800138000"
}
```

**预期 A 卡**：`FINAL_PASS` 或 `sysDecision=APPROVE`，分数 ≥788。

### 4）管理员授信（若未自动给额度）`POST /admin/risk/approve`

```json
{
  "applyId": "<submit 返回的 applyId>",
  "auditResult": "PASS",
  "creditLimit": 50000,
  "auditRemark": "B-L01 授信"
}
```

### 5）借款 `POST /loan/request`

```json
{
  "amount": 12000,
  "termMonths": 12,
  "repaymentMethod": "等额本息"
}
```

**预期**：200，`DISBURSED`；`b_card_enabled=1`；`user_b_card_log` 有 1 条。

```sql
SELECT u.real_name, l.b_card_enabled, l.b_score
FROM user u JOIN user_credit_limit l ON l.user_id = u.id
WHERE u.phone_number = '13800138101';
```

---

## B-L02 韩立成 — 两次借款（不重跑 A 卡）

### 1）注册 `POST /auth/register`

```json
{
  "realName": "韩立成",
  "phoneNumber": "13800138102",
  "email": "han_licheng@example.com",
  "idCard": "110112197204080068",
  "password": "Test123456",
  "repassword": "Test123456"
}
```

### 2）登录 `POST /auth/login`

```json
{
  "phoneNumber": "13800138102",
  "password": "Test123456"
}
```

### 3）风险评估 `POST /risk/submit`

```json
{
  "idCard": "110112197204080068",
  "name": "韩立成",
  "phone": "13800138102",
  "email": "han_licheng@example.com",
  "gender": 1,
  "birthday": "1972-04-08",
  "education": "本科",
  "marriage": "已婚",
  "jobType": "企事业单位",
  "monthlyIncome": "15000以上",
  "hasHouse": true,
  "hasCar": true,
  "contactPhone": "13800138000"
}
```

**预期 A 卡**：自动通过，分数 ≥788。

### 4）管理员授信 `POST /admin/risk/approve`

```json
{
  "applyId": "<submit 返回的 applyId>",
  "auditResult": "PASS",
  "creditLimit": 40000,
  "auditRemark": "B-L02 授信"
}
```

### 5）第一次借款 `POST /loan/request`

```json
{
  "amount": 8000,
  "termMonths": 6,
  "repaymentMethod": "等额本息"
}
```

确认 `b_card_enabled=1`。

### 6）第二次借款 `POST /loan/request`（同一 Token，不再 submit）

```json
{
  "amount": 5000,
  "termMonths": 6,
  "repaymentMethod": "等额本息"
}
```

**预期**：两笔均 200 / `DISBURSED`；无第二次 A 卡 submit；`b_card_enabled` 仍为 1。

---

## B-L03 许静雅 — 额度外借款，批贷后才开 B 卡

### 1）注册 `POST /auth/register`

```json
{
  "realName": "许静雅",
  "phoneNumber": "13800138103",
  "email": "xu_jingya@example.com",
  "idCard": "110112198203210013",
  "password": "Test123456",
  "repassword": "Test123456"
}
```

### 2）登录 `POST /auth/login`

```json
{
  "phoneNumber": "13800138103",
  "password": "Test123456"
}
```

### 3）风险评估 `POST /risk/submit`

```json
{
  "idCard": "110112198203210013",
  "name": "许静雅",
  "phone": "13800138103",
  "email": "xu_jingya@example.com",
  "gender": 0,
  "birthday": "1982-03-21",
  "education": "本科",
  "marriage": "未婚",
  "jobType": "企事业单位",
  "monthlyIncome": "8000-15000",
  "hasHouse": true,
  "hasCar": false,
  "contactPhone": "13800138000"
}
```

**预期 A 卡**：`MANUAL_REVIEW`，分数约 742–772。

### 4）管理员授信（小额度）`POST /admin/risk/approve`

```json
{
  "applyId": "<submit 返回的 applyId>",
  "auditResult": "PASS",
  "creditLimit": 15000,
  "auditRemark": "B-L03 小额度"
}
```

### 5）额度外借款 `POST /loan/request`

```json
{
  "amount": 20000,
  "termMonths": 12,
  "repaymentMethod": "等额本息"
}
```

**预期（提交后）**：200；`loan.status=PENDING_APPROVAL`；`b_card_enabled=0`；看板无许静雅。

### 6）管理员批准贷款 `POST /admin/loan/approve`

先查：`GET /admin/loan/pending-list`

```json
{
  "loanId": 1,
  "approveResult": "APPROVE",
  "additionalLimit": 10000,
  "approveRemark": "B-L03 额度外批准"
}
```

> `loanId` 换成 pending-list 返回的实际值。

**预期（批准后）**：`DISBURSED`；`b_card_enabled=1`；看板出现许静雅。

---

## B-L04 罗明远 — 放款后逾期，B 分下降

### 1）注册 `POST /auth/register`

```json
{
  "realName": "罗明远",
  "phoneNumber": "13800138104",
  "email": "luo_mingyuan@example.com",
  "idCard": "110112197905220248",
  "password": "Test123456",
  "repassword": "Test123456"
}
```

### 2）登录 `POST /auth/login`

```json
{
  "phoneNumber": "13800138104",
  "password": "Test123456"
}
```

### 3）风险评估 `POST /risk/submit`

```json
{
  "idCard": "110112197905220248",
  "name": "罗明远",
  "phone": "13800138104",
  "email": "luo_mingyuan@example.com",
  "gender": 0,
  "birthday": "1979-05-22",
  "education": "本科",
  "marriage": "已婚",
  "jobType": "企事业单位",
  "monthlyIncome": "15000以上",
  "hasHouse": true,
  "hasCar": true,
  "contactPhone": "13800138000"
}
```

**预期 A 卡**：自动通过，分数 ≥788。

### 4）管理员授信 `POST /admin/risk/approve`

```json
{
  "applyId": "<submit 返回的 applyId>",
  "auditResult": "PASS",
  "creditLimit": 50000,
  "auditRemark": "B-L04 授信"
}
```

### 5）借款 `POST /loan/request`

```json
{
  "amount": 10000,
  "termMonths": 12,
  "repaymentMethod": "等额本息"
}
```

记录此时 `b_score` 为 **基线分**。

### 6）模拟逾期 + 重算 B 分

```bash
mysql -u root -p risklendpro < sql/seed_b_card_loan_test.sql
```

然后：`POST /admin/b-card/recalculate/{userId}`（罗明远的 userId）

**预期**：`delta_score` 为负；`b_score` 低于基线；看板 `watchLevel=OVERDUE`，`daysToDue=-7`。

---

## 四角色 seed 后看板标签（可选）

四人均完成放款后执行 [`seed_b_card_loan_test.sql`](seed_b_card_loan_test.sql)，`GET /admin/b-card/monitor`：

| 角色 | 手机 | watchLevel | daysToDue |
|------|------|------------|-----------|
| 陈慧清 | 13800138101 | NORMAL | 30 |
| 韩立成 | 13800138102 | DUE_SOON | 3 |
| 许静雅 | 13800138103 | DUE_TODAY | 0 |
| 罗明远 | 13800138104 | OVERDUE | -7 |

---

## API 速查

| 接口 | 方法 | Token |
|------|------|-------|
| `/auth/register` | POST | 无 |
| `/auth/login` | POST | 无 |
| `/risk/submit` | POST | 用户 |
| `/admin/risk/approve` | POST | 管理员 |
| `/loan/request` | POST | 用户 |
| `/admin/loan/pending-list` | GET | 管理员 |
| `/admin/loan/approve` | POST | 管理员 |
| `/admin/b-card/monitor` | GET | 管理员 |
| `/admin/b-card/recalculate/{userId}` | POST | 管理员 |

---

## 常见问题

**Q：`Unknown column 'b_card_enabled'`**  
A：执行 `sql/migration_b_card.sql` 后重启应用。

**Q：submit 分数与「离线分约」偏差大**  
A：核对 `birthday` 与 `id_card` 第 7–14 位一致。

---

## 清库重测（单角色）

以陈慧清为例，手机号替换即可：

```sql
DELETE FROM user_b_card_log WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138101');
DELETE FROM repayment_record WHERE loan_id IN (SELECT loan_id FROM loan WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138101'));
DELETE FROM repayment_plan WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138101');
DELETE FROM loan WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138101');
DELETE FROM user_credit_limit WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138101');
DELETE FROM risk_assessment WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138101');
DELETE FROM user WHERE phone_number = '13800138101';
```
