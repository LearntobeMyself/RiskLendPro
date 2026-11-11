package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recommendation_impression")
public class RecommendationImpression {
    @TableId(value = "request_id", type = IdType.INPUT)
    private String requestId;
    private Long userId;
    private String scene;
    private String productIds;
    private String modelVersion;
    private String abGroup;
    private LocalDateTime createdAt;
}
