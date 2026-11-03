package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_popularity")
public class ProductPopularity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String scene;
    private Long impressions;
    private Long clicks;
    private Long applies;
    private BigDecimal ctr;
    private BigDecimal cvr;
    private BigDecimal score;
    private LocalDateTime updatedAt;
}
