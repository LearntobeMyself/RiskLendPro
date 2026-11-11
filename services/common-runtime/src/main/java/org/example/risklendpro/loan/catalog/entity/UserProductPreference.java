package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_product_preference")
public class UserProductPreference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String categoryCode;
    private String targetGroup;
    private Long tagId;
    private BigDecimal score;
    private LocalDateTime updatedAt;
}
