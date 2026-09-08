# RiskLendPro

互联网个人贷款风控系统。业务库和 Redis 已在云服务器，**接手的人不用在本机装 MySQL / Redis**。

## 一键启动

只要本机有 **JDK 21**（并加入 PATH），双击根目录 `start.bat`。

- 不用单独装 Maven（脚本用项目自带 `mvnw`，首次联网下载）
- 不用起 Gateway、user-service 等空壳
- 启动成功后业务根地址：`http://localhost:8080/api/v1`（直接打开会提示请先登录，这是正常的）
- **Swagger 文档**：`http://localhost:8080/api/v1/swagger-ui.html`（本机若用 18081，把端口换成 18081）
- 若 8080 被占用，命令行执行：`set SERVER_PORT=18081` 再运行 `start.bat`

| | 账号 | 密码 |
|---|---|---|
| 管理端 | `admin` | `Admin123456` |
| 用户端 | `13800138001` | `Test123456` |

Swagger：`http://localhost:8080/api/v1/swagger-ui.html`  
当前测试实例：`http://localhost:18081/api/v1/swagger-ui.html`

## 还需要什么

| 需要 | 不需要 |
|------|--------|
| JDK 21、能访问公网（连云库 + 首次下 Maven） | 本机 MySQL、Redis、Nacos |
| | 先跑 Python 训练（规则已在云库） |

命令行等价于：

```bat
mvnw.cmd -pl services/legacy-service spring-boot:run
```

## 仓库里各目录

| 目录 | 要不要动 |
|------|----------|
| `src/` + `services/legacy-service` | 真正在跑的后端 |
| `services/gateway` 等 | 微服务空壳，跑业务可忽略 |
| `risk-assessment/` | Python 离线训练，日常运行用不到 |
| `参考文件/` | 接口、表结构、评分卡说明 |
| `报告结构内容说明/` | 综设 III 报告与分工，不是运行说明 |
| `sql/` | 测试数据与用例 |

扣子黑名单由 Java **每周日 02:00** 触发，配置在 `src/main/resources/application.yaml`。
