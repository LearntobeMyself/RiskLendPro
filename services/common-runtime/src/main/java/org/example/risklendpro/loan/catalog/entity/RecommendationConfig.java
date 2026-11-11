package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recommendation_config")
public class RecommendationConfig {
    @TableId(value = "scene", type = IdType.INPUT)
    private String scene;
    private String configJson;
    private Integer enabled;
    private Integer version;
    private LocalDateTime updatedAt;
}
