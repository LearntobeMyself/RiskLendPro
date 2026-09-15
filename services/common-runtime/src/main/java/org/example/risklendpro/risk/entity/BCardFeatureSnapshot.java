package org.example.risklendpro.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
