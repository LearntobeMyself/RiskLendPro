package org.example.risklendpro.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_b_card_log")
public class UserBCardLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("base_score")
    private BigDecimal baseScore;
    @TableField("delta_score")
    private BigDecimal deltaScore;
    @TableField("final_score")
    private BigDecimal finalScore;
    @TableField("live_features")
    private String liveFeatures;
    @TableField("created_at")
    private Date createdAt;
}
