# RiskLendPro Web 测试台

纯 HTML/CSS/JS，用于联调授信评估 + 人工复核补充材料流程。

## 启动步骤

1. **数据库**：在 `RiskLendPro` 库执行 `sql/migration_supplement.sql`
2. **后端**：项目根目录 `mvn spring-boot:run`
3. **前端**（任选其一）：
   - VS Code 安装 Live Server，右键 `index.html` → Open with Live Server
   - 或：`npx serve test-web` 后打开提示的地址
   - 也可直接双击 `user.html` 打开（预设数据已内嵌在 JS 中，「填充」按钮无需 HTTP 加载 JSON）

4. 在首页配置 API 地址（默认 `http://localhost:8080/api/v1`）

## 页面

| 文件 | 说明 |
|------|------|
| `index.html` | 入口 + API 配置 |
| `user.html` | 用户：注册/登录/submit/status/上传补充材料 |
| `admin.html` | 管理员：列表/报告/审批/B 卡贷后监控 |

## B 卡贷后演示（管理员页第 5 节）

1. 确保已执行 `sql/migration_b_card.sql` 且 B 卡数据已入库（`load_to_mysql` 步骤 6）
2. 用户端：赵六 / 张小梁 / 吴九 分别注册 → 评估通过 → 借款放款（额度内自动 DISBURSED）
3. 执行 `mysql ... < sql/seed_b_card_demo.sql` 调整还款日
4. 管理员登录 → **5. B 卡贷后监控** → 刷新列表
5. 选中吴九 → **重算 B 分**，观察 `OVERDUE` 行 B 分下降

详见 [`sql/测试用例.md`](../sql/测试用例.md) 第五节。

## 测试用例（用户端一键填充）

每例使用**独立手机号**（13800138001–09），首次：填充 → 注册 → 登录 → 提交评估。

| 预设 | 手机 | 预期终态 | 验证点 |
|------|------|----------|--------|
| **吴九**（推荐） | 13800138008 | `MANUAL_REVIEW` | 收入异常 + 868 分，仅 `INCOME_PROOF`，补材料/去重 |
| 张小梁 | 13800138003 | `MANUAL_REVIEW` | 分数≈768，纯分数人工，可选补材料 |
| **孙七** | 13800138009 | `MANUAL_REVIEW` | L2 黑名单（`孙*`,110112）+ ~798 分，必传身份/户籍材料（需种子 SQL） |
| 李四 | 13800138004 | `SYSTEM_REJECT` | 分数≈550，纯分数拒绝 |
| 赵六 | 13800138006 | `FINAL_PASS` | 分数≈950，自动通过 |
| 吴小微 | 13800138001 | `SYSTEM_REJECT` | L3 黑名单，不算分 |
| 周八 | 13800138007 | `SYSTEM_REJECT` | 收入虚报>50%，不算分 |
| 方小巢 | 13800138002 | `SYSTEM_REJECT` | L2+低分，`RULE_AND_SCORE_LOW`（见下方重测说明） |

### 吴九快速路径（补材料联调）

1. 打开 `user.html` → 规则闸 → **吴九 (推荐)**
2. 注册 → 登录 → 提交评估 → 查询状态（应 `MANUAL_REVIEW` + `supplementStatus=REQUIRED` + `ruleTrigger=INCOME_OUTLIER`）
3. 拉取材料清单 → **一键上传** 选一张 jpg（仅 1 项 `INCOME_PROOF`）
4. 打开 `admin.html` → 登录 → 刷新列表 → 点击该行 → 预览材料 → 审批

审批通过后，后端会自动删除磁盘文件并将 `supplementStatus` 重置为 `NONE`。

### 方小巢重测（清库）

方小巢是唯一的「L2 黑名单 + 低分 → 规则+分数联合拒绝」用例。账号已使用后无法再次注册/提交，需先清库：

```sql
-- 仅当需要重测方小巢时
DELETE FROM risk_supplement_material
WHERE apply_id IN (
  SELECT apply_id FROM risk_assessment
  WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138002')
);
DELETE FROM risk_assessment
WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138002');
DELETE FROM user WHERE phone_number = '13800138002';
```

> 若终态为 `SYSTEM_REJECT` 且未清库，也可等 30 天后再次提交（业务冷却期）。

### 孙七重测（L2 + 补材料）

孙七为「L2 黑名单（姓名 `孙*` + 地域 `110112`）+ ~798 分 → 人工 + 必传身份/户籍材料」。首次测试前执行 `sql/seed_blacklist_sunqi_l2.sql` 或 `load_to_mysql.py`。重测清库：

```sql
DELETE FROM risk_supplement_material
WHERE apply_id IN (
  SELECT apply_id FROM risk_assessment
  WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138009')
);
DELETE FROM risk_assessment
WHERE user_id = (SELECT id FROM user WHERE phone_number = '13800138009');
DELETE FROM user WHERE phone_number = '13800138009';
```

登录后若已有进行中申请，页面会显示「暂不可重复提交」横幅（`GET /risk/assessment/submit-eligibility`）。

## 预设数据

- **`js/presets.js`** — 8 个用例一键填充（内嵌，不依赖 fetch）
- `presets/*.json` — 与内嵌数据相同的参考副本（如有）

## 注意

- 用户 Token 与管理员 Token 分别存在 localStorage
- 跨域依赖后端 CORS（已配置）
- 管理员登录路径 `/admin/login` 已在 SecurityConfig 与 JwtFilter 白名单
