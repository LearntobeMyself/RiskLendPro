package org.example.risklendpro.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * B 卡 V2 贷后特征快照（阶段 A2：as-of 快照与成熟标签）。
 *
 * <p>命名约定：
 * <ul>
 *   <li>{@code observationStart/End}：观察期（asOfDate 之前 180 天），特征只允许使用该区间内 asOfDate 当日及以前的数据；</li>
 *   <li>{@code performanceStart/End}：表现期起点为 asOfDate+1，终点取两个标签中最长 horizon（12 个月）；</li>
 *   <li>{@code maturityDate30dpd6m / maturityDate90dpd12m}：各标签的表现期结束日，只有到该日期之后才允许写入对应标签；</li>
 *   <li>{@code sampleStatus}：OPEN（无标签成熟）/ MATURED（部分成熟，等待或部分完成标签）/ LABELED（两个标签齐备，可训练）/ EXCLUDED（数据质量不合格，排除训练）。</li>
 * </ul>
 */
@Data
@TableName("b_card_feature_snapshot")
public class BCardFeatureSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("as_of_date")
    private LocalDate asOfDate;

    @TableField("snapshot_time")
    private LocalDateTime snapshotTime;

    @TableField("feature_version")
    private String featureVersion;

    @TableField("observation_start")
    private LocalDate observationStart;

    @TableField("observation_end")
    private LocalDate observationEnd;

    @TableField("performance_start")
    private LocalDate performanceStart;

    @TableField("performance_end")
    private LocalDate performanceEnd;

    @TableField("sample_status")
    private String sampleStatus;

    @TableField("label_30dpd_6m")
    private Integer label30dpd6m;

    @TableField("label_90dpd_12m")
    private Integer label90dpd12m;

    @TableField("feature_json")
    private String featureJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // ---------------- A2 新增：标签成熟与数据质量元数据 ----------------

    /** label_30dpd_6m 的表现期结束日，可达该日期之后才允许生成该标签。 */
    @TableField("maturity_date_30dpd_6m")
    private LocalDate maturityDate30dpd6m;

    /** label_90dpd_12m 的表现期结束日。 */
    @TableField("maturity_date_90dpd_12m")
    private LocalDate maturityDate90dpd12m;

    /** 标签口径版本，训练导出必须与之一致。 */
    @TableField("label_version")
    private String labelVersion;

    /** 标签实际计算时间。 */
    @TableField("label_calculated_at")
    private LocalDateTime labelCalculatedAt;

    /** 数据质量状态：OK / DEGRADED / INSUFFICIENT。 */
    @TableField("data_quality_status")
    private String dataQualityStatus;

    /** 特征 JSON 的内容 SHA-256，用于历史复现与一致性校验。 */
    @TableField("feature_hash")
    private String featureHash;

    /** 特征数据来源说明，例如 RiskLendPro:loan/repayment_plan/repayment_record/user_credit_limit_history。 */
    @TableField("data_source")
    private String dataSource;
}