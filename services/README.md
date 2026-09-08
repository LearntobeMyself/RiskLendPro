# RiskLendPro 微服务迁移

当前采用绞杀式迁移：`legacy-service` 保留综设 II 已验证功能，Gateway 作为唯一入口，新接口直接进入独立服务；旧接口逐个迁出后删除 legacy 模块。

**接手后要先跑通现有功能：只启动 `legacy-service`。** 其余模块还没有迁过去的业务接口，全部拉起来也不能替代单体。

## 模块与端口

| 模块 | 默认端口 | 当前职责 |
|------|----------|----------|
| `common-api` | 无 | 跨服务 DTO 与 HTTP 契约，不含数据库代码 |
| `gateway` | 8088 | JWT 校验、管理员权限隔离、统一路由 |
| `user-service` | 8081 | 用户与认证迁移目标 |
| `risk-service` | 8082 | 风控迁移目标 |
| `loan-service` | 8083 | 消费贷及借还款迁移目标 |
| `cs-service` | 8084 | 智能客服独立服务 |
| `legacy-service` | 8080 | 迁移期间保留的综设 II 功能 |

## 构建

```powershell
mvn clean package
```

## 本地启动顺序

只跑业务：

```powershell
mvn -pl services/legacy-service spring-boot:run
```

不要加 `-am`（会去跑父 POM，报没有 main class）。`user-service` / `risk-service` / `loan-service` 依赖 `common-api`，若要起空壳需先 `mvn -pl common-api install -DskipTests`。

全部模块（骨架联调，非必需）：

```powershell
mvn -pl services/user-service spring-boot:run
mvn -pl services/risk-service spring-boot:run
mvn -pl services/loan-service spring-boot:run
mvn -pl services/cs-service spring-boot:run
mvn -pl services/gateway spring-boot:run
```

客户端统一使用 `http://localhost:8088`。迁移期间，原有 `/api/v1/**` 默认转发到 legacy；`/api/v1/cs/**` 已转发到客服服务。

健康检查：

- `/platform/user/health`
- `/platform/risk/health`
- `/platform/loan/health`
- `/platform/cs/health`

## 迁移规则

1. 新功能不得继续加入根目录单体。
2. 每迁移一个接口，就把对应 Gateway 路由从 legacy 切换到目标服务。
3. 服务之间使用 Feign/HTTP，不得直接依赖其他服务的 Mapper。
4. `credit_data_db` 只能由 `risk-service` 访问。
5. 一张业务表只有一个服务负责写入。

已定义的内部契约包括用户摘要、额度授予/查询、最终风控结果、B 卡重算和贷款行为摘要。`user-service`、`risk-service`、`loan-service` 已各自声明 Feign 客户端；业务迁移时用这些客户端替换原有跨域 Mapper。
