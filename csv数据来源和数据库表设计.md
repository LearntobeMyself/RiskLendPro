# 评分规则csv

李老师提供CSV数据说明

## 一、数据来源

本数据集来自 **Lending Club**（美国最大的P2P借贷平台），是风控领域非常经典的公开数据集。

## 二、文件清单

| 文件                                      | 类型    | 说明                 |
| :-------------------------------------- | :---- | :----------------- |
| `loan.csv`                              | CSV   | 主数据集，包含贷款申请和还款记录   |
| `LCDataDictionary.xlsx`                 | Excel | 数据字典，详细解释每个字段含义    |
| `grouped_table.json`                    | JSON  | 分组统计信息（违约 vs 正常还款） |
| `Receiver_Operating_Characteristic.png` | PNG   | ROC曲线，模型评估指标可视化    |

## 三、关键字段说明

### 3.1 基本信息

| 字段        | 说明   | 示例值                               |
| :-------- | :--- | :-------------------------------- |
| `term`    | 贷款期限 | 36 months / 60 months             |
| `grade`   | 贷款等级 | A, B, C, D, E                     |
| `purpose` | 贷款用途 | credit\_card, debt\_consolidation |

### 3.2 财务信息

| 字段           | 说明      |
| :----------- | :------ |
| `annual_inc` | 年收入     |
| `dti`        | 债务收入比   |
| `revol_bal`  | 循环信用余额  |
| `revol_util` | 循环信用利用率 |

### 3.3 征信信息

| 字段               | 说明                                                          |
| :--------------- | :---------------------------------------------------------- |
| `inq_last_6mths` | 近6个月征信查询次数                                                  |
| `open_acc`       | 未结清账户数                                                      |
| `total_acc`      | 总账户数                                                        |
| `delinq_2yrs`    | 近两年逾期次数（申请时点可得，常见字段名亦可能是数据字典中的等价列）                          |
| `grade`          | 贷款等级 A–G（用于训练侧 **edu\_tier** 三档，对应推理侧学历枚举）                  |
| `home_ownership` | 住房权属 OWN / MORTGAGE / RENT 等（用于 **house\_owner**，对齐推理侧是否有房） |
| `emp_length`     | 工作年限描述（用于 **job\_stable**，对齐推理侧单位性质：公务员/企事业单位）              |

### 3.4 标签字段

| 字段            | 说明   | 违约标识                          |
| :------------ | :--- | :---------------------------- |
| `loan_status` | 贷款状态 | Charged Off=违约, Fully Paid=正常 |

## 四、字段映射建议

用于训练风控模型时的字段映射：

| 模型字段                    | 数据源字段               | 转换方式                                                                                                          |
| :---------------------- | :------------------ | :------------------------------------------------------------------------------------------------------------ |
| `income`                | `annual_inc`        | 除以12得到月收入                                                                                                     |
| `credit_query_count_3m` | `inq_last_6mths`    | 乘以0.5估算                                                                                                       |
| `multi_head_loan_count` | `open_acc`          | 直接使用                                                                                                          |
| `overdue_count_12m`     | `delinq_2yrs`（或等价列） | 缺失填 0，`clip` 到合理上限；**禁止**用 `loan_status` 反推逾期，否则与标签 `defaulted` 泄漏等价，推理评分会饱和在极端区间                             |
| `edu_tier`（模型内部）        | `grade`             | **high**：A、B；**mid**：C；**low**：D–G 或缺失。推理时中文学历映射见 `output/scoring_rules.json` 内 `feature_derivation.edu_tier` |
| `house_owner`（0/1）      | `home_ownership`    | OWN 或 MORTGAGE→1，否则→0。推理时对应申请表 **hasHouse**                                                                   |
| `job_stable`（0/1）       | `emp_length`        | 含「10+」或解析年限≥5→1，否则→0。推理时 **jobType** 为公务员、企事业单位→1                                                             |
| `has_car_stated`（0/1）   | （LC 通常无）            | 训练侧恒为 0；有车系数依赖模拟数据或后续含车字段的 CSV。推理时对应 **hasCar**                                                               |
| `marriage_married`（0/1） | （LC 通常无）            | 训练矩阵一般不包含该列；模拟训练可有。推理时 **已婚**→1                                                                               |
| `defaulted`             | `loan_status`       | Charged Off→1, Fully Paid→0（仅作标签 Y）                                                                           |

## 五、使用建议

1. **数据准备**：将 `loan.csv` 复制到 `risk-assessment/data/training_data.csv`
2. **模型训练**：运行 `python train_scoring_model.py` 进行训练
3. **结果分析**：查看 `output/scoring_rules.json` 获取特征权重

## 六、统计信息

从 `grouped_table.json` 可以看出：

- **正常还款（Fully Paid）**：约36,000条
- **违约（Charged Off）**：约6,400条
- **样本比例**：约85%正常 vs 15%违约

# 黑名单数据来源csv

### **1. 官方浙江温州市——失信被执行人数据（2497条）**

数据量：2497条

**下载链接**：

- [CSV格式](https://data.wenzhou.gov.cn/jdop_front/detail/data.do?iid=13341\&searchString=)
- XLS、XML、JSON、RDF格式也可以在同一页面下载

包含字段：被执行人姓名/名称、案号、执行法院、立案时间、执行标的金额、法定代表人、组织机构代码等

### 清洗后数据结构表为

1. **name** (被执行人姓名/名称)：匹配的第一维度。
2. **area\_code** (由 执行法院 转化)：匹配的第二维度（地域）。例如“北京市通州区”对应 110112。
3. **birth\_year** (从 出生日期 提取)：匹配的第三维度（年龄）。提取 1977。
4. **case\_no** (案号)：用于人工审批时，管理员核对具体案件。
5. **court\_name** (执行法院)：辅助展示。
6. **duty\_status** (被执行人的履行情况)：判定风险严重程度。如“全部未履行”是最高风险。
7. **behavior\_details** (失信被执行人行为情况)：具体原因，如“有履行能力而拒不履行”。

<br />

# 用户行为特征数据来源csv

**数据来源：** 本项目使用的数据源自 Kaggle 上的 [Home Credit Default Risk](https://www.kaggle.com/competitions/home-credit-default-risk) 竞赛数据集。

**数据集说明：** 具体使用的文件为 `home_credit_train_ready.csv`，由 [jamirc](https://huggingface.co/datasets/jamirc/home_credit_default_risk) 在 Hugging Face 平台托管。该数据包含了 Home Credit 提供的客户申请信息，目标是预测客户是否具有违约风险。数据经过了预处理，包含了用于构建预测模型的静态特征和历史信贷记录衍生特征。

<br />

### 清洗后的数据结构为

| **类别**    | **字段名 (HC 原始名)**               | **业务意义**   | **用途**              |
| :-------- | :----------------------------- | :--------- | :------------------ |
| **主键**    | SK\_ID\_CURR                   | 用户关联 ID    | 用于 Java 查询映射        |
| **基础事实**  | DAYS\_BIRTH                    | 出生日期       | **验真**：看用户填的年龄对不对   |
| <br />    | DAYS\_EMPLOYED                 | 入职天数       | **验真**：看用户填的工作年限对不对 |
| <br />    | AMT\_INCOME\_TOTAL             | **后台记录收入** | **验真**：看用户填的收入是否造假  |
| **多头/行为** | AMT\_REQ\_CREDIT\_BUREAU\_WEEK | 近1周征信查询    | **评分**：评估多头风险       |
| <br />    | AMT\_REQ\_CREDIT\_BUREAU\_MON  | 近1月征信查询    | **评分**：评估多头风险       |
| <br />    | DAYS\_LAST\_PHONE\_CHANGE      | 手机换号天数     | **评分**：评估稳定性        |
| <br />    | active\_loans\_count           | 活跃贷款数      | **评分/验真**：负债水平      |
| **外部权威**  | EXT\_SOURCE\_2                 | 第三方评分 A    | **评分**：权重极高，代表权威评价  |
| <br />    | EXT\_SOURCE\_3                 | 第三方评分 B    | **评分**：补充权威评价       |
| **资产/现状** | FLAG\_OWN\_CAR                 | 是否有车       | **验真**：核实资产         |
| <br />    | OCCUPATION\_TYPE               | 职业类型       | **评分**：职业风险分级       |
| <br />    | NAME\_EDUCATION\_TYPE          | 学历         | **验真**：核实背景         |
| **历史表现**  | TARGET                         | 历史标签 (0/1) | **回测**：验证你的模型准不准    |
| <br />    | prev\_refused\_count           | 历史被拒次数     | **拦截**：有过往严重风险则拒绝   |

