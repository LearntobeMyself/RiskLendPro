# 全 Controller 潜在问题排查报告

- 排查日期：2026-10-21
- 排查范围：services 下全部 28 个 Controller 及其直接调用的 Service 实现（含 gateway 公共鉴权）
- 排查方式：逐个 Controller 阅读入口方法 → 追踪对应 Service 的关键分支（权限、归属校验、事务边界、并发、空值、分页、Feign 调用）
- 说明：本报告为**静态排查结论，未修改任何代码**；与 `docs/testing/贷后催收与还款排查报告.md` 互补
- 严重度定义：高＝可直接造成资金/数据错误或安全漏洞；中＝特定条件下报错、数据不一致或明显性能问题；低＝健壮性/体验/加固建议

---

## 一、结论总览

| 服务 | Controller | 结论 | 主要问题 |
|---|---|---|---|
| user | AuthController | 有风险 | 注册唯一性/非空校验缺失导致 500 |
| user | UserController | 有风险 | 无额度记录时 500 |
| user | AdminAuthController | 正常 | — |
| user | AdminUserController | 有风险 | 导出 N+1、唯一键冲突 500 |
| user | AdminSystemController | **高危** | 备份下载路径穿越；操作日志全量+N+1 |
| user | InternalUserController | 低风险 | 全表/模糊查询无上限 |
| risk | RiskAssessmentController | 基本正常 | 冷却天数毫秒截断 |
| risk | RiskSupplementController | 低风险 | 仅校验扩展名 |
| risk | AdminSupplementController | 有风险 | 存储型 XSS、文件名注入 |
| risk | AdminRiskController | 正常 | — |
| risk | AdminRiskDataController | 有风险 | 全量用户 N+1、导出无分页 |
| risk | BCardGovernanceController | 低风险 | 缺方法级鉴权（仅靠网关） |
| risk | BlacklistSyncController | 正常 | 已由静态 token 保护 |
| risk | InternalRiskController | 正常 | — |
| risk | InternalRiskAdminQueryController | 低风险 | 全量日志 + N+1 |
| risk | BehaviorRuleRefreshController | 正常 | — |
| risk | ImageVerificationDemoController | 待确认 | 生产需确认开关关闭 |
| loan | LoanController | 有风险 | 请求体无校验、分页无上限 |
| loan | RepaymentController | 正常 | 归属/足额/期次校验齐全 |
| loan | AdminLoanController | 有风险 | 导出 N+1、状态过滤被忽略 |
| loan | AdminLoanOpsController | 低风险 | 审批提示文案不实 |
| loan | AdminCreditController | **高危** | 调额会绕过抵押审核自动放款；事务含 Feign |
| loan | AdminRepaymentController | 有风险 | 逾期金额口径不一致、N+1 |
| loan | UserLoanProductController | 正常 | — |
| loan | AdminLoanProductController | 正常 | 预签名已校验类型/大小 |
| loan | InternalLoanController | 有风险 | 并发额度初始化 500、在贷余额虚高 |
| loan | InternalLoanAdminQueryController | 有风险 | 全量日志 + 每行 Feign |
| cs | CsStatusController | 正常 | — |

---

## 二、user-service

### 2.1 AuthController（`/auth`）

**U-01（中）注册只校验手机号唯一性，身份证/邮箱重复直接 500**
- 位置：`user/auth/AuthServiceImpl.java` 注册分支
- 现象：同一身份证或邮箱重复注册时，插入触发数据库唯一键冲突，`GlobalExceptionHandler` 将 `DataAccessException` 统一返回 500「服务器内部错误」。
- 根因：代码只做了 `selectCount(phone)` 预校验；`user` 表对 `phone_number`、`id_card` 均有唯一键。
- 影响：注册页出现 500，用户无法自助判断原因；同一身份证可绕过预检直接撞库。
- 建议：对 `idCard`/`email` 增加预校验，并捕获 `DuplicateKeyException` 返回「该身份证/邮箱已被注册」。

**U-02（中）注册未校验 `real_name`/`email`/`id_card` 非空**
- 位置：同上；`user` 表这三列均为 `NOT NULL`
- 现象：请求体缺字段时插入失败 → 500。
- 建议：DTO 增加 `@NotBlank` 并让 Controller 使用 `@Valid`。

**U-03（低）登录无失败次数限制**
- 现象：`/auth/login`、`/admin/login` 可无限次尝试，无锁定或限流。
- 建议：网关或认证服务增加失败计数/速率限制。

### 2.2 UserController（`/user`）

**U-04（中）用户无额度记录时 `/user/credit-limit` 返回 500**
- 位置：`UserController.creditLimit` → `UserCreditLimitServiceImpl.getUserCreditLimit`
- 现象：新注册、尚未授信的用户查询额度，服务抛「用户额度信息不存在」→ 500。
- 根因：`InternalLoanApi.ensureCreditLimit` 已实现（可幂等补零额度），但该入口未调用它。
- 影响：新用户首页额度卡片直接报错。
- 建议：查询前调用 `ensureCreditLimit`，或返回 0 额度快照。

### 2.3 AdminAuthController（`/admin/login`、`/admin/profile`）

未发现问题。`/admin/profile` 在网关 `AdminPathAccess` 中对任意 staff 放行，非仅 SYS_ADMIN。

### 2.4 AdminUserController（`/admin/users`）

**U-05（中）用户导出全量加载 + 逐行 Feign（N+1）**
- 位置：`AdminUserServiceImpl.exportUsers`
- 现象：先 `selectList` 全量用户，再对每一行调用 `loanServiceClient.getCreditLimit`。1 万用户 → 1 万次 HTTP。
- 影响：导出接口超时、线程池被占满。
- 建议：改用 `listCreditLimits(userIds)` 批量接口；导出增加时间范围或上限。

**U-06（中）管理员创建/编辑用户未处理唯一键与必填**
- 位置：`AdminUserServiceImpl.createUser` / `updateUser`
- 现象：手机号/身份证改重 → 500；`idCard` 为空 → 数据库 NOT NULL → 500。
- 建议：与 U-01/U-02 同样处理。

### 2.5 AdminSystemController（`/admin/system`）

**U-07（高）备份下载存在路径穿越风险**
- 位置：`AdminSystemServiceImpl.downloadBackup`（`Paths.get(backupDir, backupId)` 直接拼接）
- 现象：`backupId` 为路径参数，未做 `normalize()` 与「必须仍在备份目录内」校验；构造 `..\..\` 类路径可读取备份目录外文件（是否可达取决于容器对编码斜杠的策略）。
- 影响：管理员令牌泄露时，可读取服务器任意文件。
- 建议：`Path target = backupDir.resolve(backupId).normalize()`，再校验 `target.startsWith(backupDir)`，并对 `backupId` 做白名单格式（如 `[A-Za-z0-9_-]+`）。

**U-08（中）操作日志每次请求全量拉取 + 多层 N+1**
- 位置：`AdminSystemServiceImpl.listOperationLogs`
- 现象：每次分页请求都把 loan/risk 两域**全部**日志拉回内存，再排序分页；`InternalLoanAdminQueryController`/`InternalRiskAdminQueryController` 内还对每行日志调用一次 `getAdmin`。
- 影响：日志量增长后该页面必然超时（N 条日志 = N 次 Feign）。
- 建议：查询下沉到各域按时间范围+分页返回，或在聚合层缓存管理员映射。

**U-09（低）恢复备份为占位实现**
- `restoreBackup` 只返回 `RESTORED`，并未真正恢复；同时把不存在的 `backupId` 也当作成功（存在性探测）。
- `deleteAdmin` 用 `selectCount(null) <= 1` 判断最后一个管理员，并发删除可同时通过。

### 2.6 InternalUserController（`/internal/users`）

**U-10（低）模糊查询与全表查询无上限**
- `searchUserIds` 使用 `like` 且无 limit；`listAllUsers` 返回全表，被多个服务的「批量」接口调用。
- 影响：用户量增大后单次响应可达数十 MB，且调用方再逐条兜底时放大为 N+1。
- 建议：`listAllUsers` 改为按 id 集合查询或加分页；`searchUserIds` 加 `LIMIT`。

**U-11（低）`updateAssessmentStatus` 无状态白名单**
- 任意字符串均可写入 `assessment_status`，可能污染风控判断。

---

## 三、risk-service

### 3.1 RiskAssessmentController（`/risk/assessment`）

**R-01（低）冷却期天数用毫秒截断**
- `calculateDaysSinceLastApply` 用 `(now - submitTime)/86400000`，与已修复的催收天数问题同源，边界随提交时刻漂移（±1 天）。建议改为自然日计算。

其余校验正常：申请时身份证与注册信息一致性已校验；`applyId` 归属已校验。

### 3.2 RiskSupplementController（`/risk/supplement`）

**R-02（低）上传仅校验扩展名与大小**
- `FileStorageService.validateFile` 不校验 MIME/文件头，`mimeType` 直接取客户端 `Content-Type` 并入库。

### 3.3 AdminSupplementController（`/admin/supplement`）

**R-03（中）客户端可控 MIME + inline 打开 → 存储型 XSS**
- 位置：`download` 使用 `material.getMimeType()` 作为响应 `Content-Type`，`inline=true` 时浏览器直接渲染。
- 现象：用户上传 `x.pdf`（扩展名通过校验）但声明 `Content-Type: text/html` 且内容为脚本，管理员点击预览即在管理端域内执行。
- 建议：响应 `Content-Type` 由扩展名白名单推导，`inline` 仅允许 `image/*`、`application/pdf`，并加 `X-Content-Type-Options: nosniff`。

**R-04（中）`Content-Disposition` 直接拼接原始文件名**
- 位置：`disposition + "; filename=\"" + material.getOriginalName() + "\""`
- 现象：中文文件名会乱码或导致响应头非法；文件名含 `"`/换行时可破坏响应头（下载文件名被篡改）。
- 建议：仅保留安全字符，并使用 RFC 5987 `filename*=UTF-8''...` 编码。

**R-05（低）非法 MIME 触发 500**
- `MediaType.parseMediaType(contentType)` 对客户端脏数据抛 `InvalidMediaTypeException` → 500。

### 3.4 AdminRiskController（`/admin/risk`）

未发现问题；审批接口已通过 `mailed` 标志如实返回邮件发送结果（优于贷款侧）。

### 3.5 AdminRiskDataController（`/admin/risk` 数据域）

**R-06（中）多笔在贷名单：全量用户 × 逐人查询**
- 位置：`AdminRiskDataServiceImpl.listMultiLoan`
- 现象：先 `listAllUsers()` 取全量用户，再对每人执行一次 `userExternalFeaturesMapper.selectByIdCard`。1 万用户 → 1 万次 DB 查询。
- 建议：改为一次 `in (id_cards)` 批量查询后在内存匹配。

**R-07（低）风险数据导出全量无分页**
- `exportRiskData` 一次性加载并写 CSV；`String.valueOf(null)` 会输出字面量 `"null"`。

### 3.6 BCardGovernanceController（`/admin/b-card`）

**R-08（低）仅依赖网关鉴权**
- 类上无 `@PreAuthorize`（同项目其他管理端均有），模型激活/回滚等写操作只经网关 `risk:monitor:write`；缺少二次确认与服务层权限校验。
- 建议：补 `@PreAuthorize` 做纵深防御。

### 3.7 BlacklistSyncController（`/sync/blacklist`）

未发现问题。网关将其放行到服务，但 `JwtFilter` 用 `risk.blacklist-sync.api-token` 静态令牌校验，符合设计。**注意**：该 token 在 `application-common.yml` 中存在硬编码默认值（见 G-03）。

### 3.8 InternalRiskController（`/internal/risk`）

未发现问题：放款闸门在评估不存在或状态非 `FINAL_PASS` 时均拒绝。

### 3.9 InternalRiskAdminQueryController（`/internal/risk-admin`）

**R-09（低）全量日志 + 每行 Feign 解析操作人**
- 与 U-08 同源，建议合并治理。

### 3.10 BehaviorRuleRefreshController（`/internal/...`）

未发现问题（内部契约接口）。

### 3.11 ImageVerificationDemoController

**R-10（待确认）演示接口需确认生产关闭**
- 受 `risk.demo.image-verify.enabled` 与网关白名单控制；上线前应确认为 `false`。

---

## 四、loan-service

### 4.1 LoanController（`/loan`）

**L-01（中）借款请求体无 `@Valid`**
- `LoanRequest` 的 `amount`/`termMonths`/`repaymentMethod` 为 null 时，服务内 `request.getAmount().compareTo(...)` 直接 NPE → 500。
- 建议：DTO 加 `@NotNull`/`@DecimalMin`，Controller 加 `@Valid`。

**L-02（低）分页参数无上限**
- `pageSize` 可传极大值，导致大结果集查询与序列化压力。建议统一 `min(size, 100)`。

### 4.2 RepaymentController（`/repayment`）

未发现问题：还款执行具备 FOR UPDATE 行锁、归属校验、当前期次校验、足额校验、重复还款幂等；还款记录查询具备归属校验。

### 4.3 AdminLoanController（`/admin/loan`）

**L-03（中）贷款记录导出全量 + 逐行 Feign**
- `exportRecords` 先全量查 Loan，再对每行 `buildRecordItem`（内部 `getUser`、查还款计划）。
- 建议：批量预取用户/计划，导出加分页或上限。

**L-04（中）审批记录列表每行 2 次 Feign**
- `listApprovalRecords` 每行 `getUser` + `getAdmin`；单页 20 行即 40 次 HTTP。

**L-05（低）状态过滤被静默忽略**
- 只识别 `APPROVED`/`REJECTED`，传其它值时不追加条件，调用方会拿到非预期数据。

### 4.4 AdminLoanOpsController（`/admin/loan`）

**L-06（低）审批返回文案固定为「已邮件通知用户」**
- 邮件实际失败时文案不实；建议像 `AdminRiskController` 一样返回 `mailed` 标志。

### 4.5 AdminCreditController（`/admin/credit`）

**L-07（高）调额会自动放款抵押类挂起申请，绕过管理员抵押物审核**
- 位置：`UserCreditLimitServiceImpl.adjustLimit` → `autoDisbursePendingLoans`
- 事实链：
  1. `PENDING_APPROVAL` 只由**抵押类**申请产生（`LoanServiceImpl` 抵押分支），语义是「等待管理员确认抵押物后放款」；
  2. 调额成功后该方法把该用户全部 `PENDING_APPROVAL` 借据直接置为 `DISBURSED`、生成还款计划；
  3. 未过滤 `collateral_type`、未校验抵押物是否已确认/估值、未过 `getBorrowGate`、未检查逾期；
  4. 还对这些抵押借据调用 `deductCreditLimit`，而抵押贷款本不占用信用额度。
- 影响：一次调额即可让未经抵押审核的房抵/车抵申请自动放款，并造成额度账务错误。
- 建议：自动放款仅限信用类（`collateral_type IS NULL`），并复用与借款一致的闸门与逾期校验；抵押类必须保持人工审批。

**L-08（中）调额事务内含 Feign 与邮件**
- `adjustLimit` 标注 `@Transactional`，内部调用 `userServiceClient.getUser`、`riskServiceClient.activateBehaviorScore`、`emailUtil.sendLoanSuccessNotification`。
- 违反项目约束「事务边界不得包含 Feign 调用」；长事务持有 `FOR UPDATE` 行锁等待外部 HTTP/SMTP。
- 建议：事务内只做额度/日志/借据落库，Feign 与邮件移到事务提交后。

**L-09（中）批量调额异常信息为 null 时整批 500**
- `failed.add(Map.of("userId", userId, "reason", e.getMessage()))`；`Map.of` 不允许 null 值，`e.getMessage()==null`（如裸 NPE）会抛 NPE，导致整个批量接口失败。
- 建议：`e.getMessage() != null ? ... : e.getClass().getSimpleName()`，或改用 `HashMap`。

**L-10（中）批量调额参数未校验**
- `request.getUserIds()` 为 null 或其元素为 null 时 NPE → 500。

**L-11（低）调额幅度未校验**
- `calcNewLimit` 允许 `DECREASE` 传负数（变成提额）、`PERCENT` 传任意值；建议校验 `value > 0` 与百分比范围。

**L-12（低）额度统计全表加载**
- `getStats`、`listLimits(status 过滤)` 全表 `selectList`；`prefetch` 批量失败时退化为逐用户 Feign。

### 4.6 AdminRepaymentController（`/admin/repayment`）

**L-13（中）逾期金额统计口径不一致**
- `getSummary().overdueAmount` 汇总 `plan.remainingAmount`（含全部未到期期次），而信用额度里的 `overdueAmount` 汇总的是「已逾期期次金额」。
- 影响：同一用户/全平台在两处看到的逾期金额不同，管理端数值明显虚高。
- 建议：统一按「OVERDUE 还款记录金额之和」计算。

**L-14（中）实收/逾期列表逐行 Feign + 查询**
- `listActualRecords` 每行 `loanMapper.selectById` + `getUser`；`listOverdueRecords` 每行 `getUser`。

**L-15（低）报表全量导出；提醒接口邮件失败仍返回成功**
- `generateReport` 无分页；`sendReminder` 在 `email==null` 或发送失败时仍返回 `reminderId`。

### 4.7 UserLoanProductController（`/loan/products`）

未发现问题。

### 4.8 AdminLoanProductController（`/admin/products`）

未发现问题：预签名校验了 `contentType` 白名单（jpeg/png/webp）、大小 ≤5MB，对象键由服务端生成 UUID。

### 4.9 InternalLoanController（`/internal/loan`）

**L-16（中）额度初始化并发 500**
- 位置：`ensureCreditLimit`
- 现象：先 `selectOne` 再 `insert`，无锁也无 `DuplicateKeyException` 捕获。并发首次调用（如多个页面同时加载）会触发 `user_id` 唯一键冲突 → 500。
- 建议：捕获 `DuplicateKeyException` 后重新查询返回。

**L-17（中）首次授信并发重复插入**
- `grantCreditLimit` 用 `FOR UPDATE` 加锁，但记录不存在时锁不到行；两个并发首次授信都会走 insert。
- 建议：先 `ensureCreditLimit` 建行再加锁，或捕获唯一键冲突重试。

**L-18（中）在贷余额口径错误**
- `getLoanBehavior().outstandingAmount` 对在贷/逾期借据累加的是**原始借款金额**，而非剩余本金。
- 影响：B 卡/行为评分中的「在贷余额」系统性虚高，可能影响额度系数。
- 建议：按还款计划 `remainingAmount` 汇总。

**L-19（低）行为分写入无锁，存在丢失更新**
- `upsertBehaviorScore` 读-改-写无 `FOR UPDATE`，并发时 B 分/开关可能被旧值覆盖。

### 4.10 InternalLoanAdminQueryController（`/internal/loan-admin`）

**L-20（中）全量日志列表 + 每行 Feign 解析操作人**
- 两个接口均 `selectList` 全表，再逐行 `getAdmin`。被 user-service 操作日志页每次调用，属于「全量×N+1」最坏组合。

### 4.11 CsStatusController（`/cs/status`）

未发现问题。

---

## 五、网关与公共组件

**G-01（低）JwtFilter 白名单使用子串匹配**
- `contains("/auth/")`、`contains("/admin/login")`、`contains("/internal/")` 这类判断过宽：未来新增任何包含该子串的路径都会意外免鉴权。
- 建议：改为精确路径或 `startsWith` 前缀白名单。

**G-02（中）登录接口无速率限制**
- 全仓库仅有演示接口有 `DemoRequestRateLimiter`，`/auth/login` 与 `/admin/login` 无失败计数/锁定，可被暴力破解。

**G-03（低）内部通信令牌存在硬编码默认值**
- `application-common.yml` 中 `internal.api-token`、`risk.blacklist-sync.api-token` 均有默认值，未通过环境变量强制覆盖时生产环境等同于公开密钥。

**G-04（低）唯一键冲突统一返回 500**
- `GlobalExceptionHandler` 只把 1020/1205/1213 视为冲突，MySQL 1062（唯一键）落入 500 分支。
- 这是 U-01、U-06、L-16、L-17 表现为「服务器内部错误」的共同原因。建议识别 1062 返回 409 + 友好文案。

---

## 六、建议修复优先级

| 优先级 | 条目 | 理由 |
|---|---|---|
| P0 | L-07 调额自动放款抵押申请 | 绕过抵押审核直接放款，且额度账务错误 |
| P0 | U-07 备份下载路径穿越 | 任意文件读取风险 |
| P1 | L-08 调额事务含 Feign/邮件 | 违反项目硬约束，易造成锁等待与回滚 |
| P1 | R-03 补充材料存储型 XSS | 管理端会话可被脚本劫持 |
| P1 | U-04 / U-01 / L-16 / L-17 | 高频入口 500，用户可直接感知 |
| P2 | U-05、U-08、L-03、L-04、L-13、L-14、R-06、L-20 | 性能与统计口径问题，数据量增长后放大 |
| P3 | 其余低风险项 | 健壮性与安全加固 |

---

## 七、本次未改动代码

本报告仅记录问题。若需修复，建议按上表优先级分小步提交，每步单独验证；其中 P0/P1 涉及资金与安全，建议先确认业务预期（尤其是 L-07 自动放款的原始设计意图）再改。
