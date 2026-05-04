# 李老师提供的CSV数据说明

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

| 字段               | 说明         |
| :--------------- | :--------- |
| `inq_last_6mths` | 近6个月征信查询次数 |
| `open_acc`       | 未结清账户数     |
| `total_acc`      | 总账户数       |

### 3.4 标签字段

| 字段            | 说明   | 违约标识                          |
| :------------ | :--- | :---------------------------- |
| `loan_status` | 贷款状态 | Charged Off=违约, Fully Paid=正常 |

## 四、字段映射建议

用于训练风控模型时的字段映射：

| 模型字段                    | 数据源字段            | 转换方式                        |
| :---------------------- | :--------------- | :-------------------------- |
| `income`                | `annual_inc`     | 除以12得到月收入                   |
| `credit_query_count_3m` | `inq_last_6mths` | 乘以0.5估算                     |
| `multi_head_loan_count` | `open_acc`       | 直接使用                        |
| `defaulted`             | `loan_status`    | Charged Off→1, Fully Paid→0 |

## 五、使用建议

1. **数据准备**：将 `loan.csv` 复制到 `risk-assessment/data/training_data.csv`
2. **模型训练**：运行 `python train_scoring_model.py` 进行训练
3. **结果分析**：查看 `output/scoring_rules.json` 获取特征权重

## 六、统计信息

从 `grouped_table.json` 可以看出：

- **正常还款（Fully Paid）**：约36,000条
- **违约（Charged Off）**：约6,400条
- **样本比例**：约85%正常 vs 15%违约

