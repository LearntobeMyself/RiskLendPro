package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_similarity")
public class ProductSimilarity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long similarProductId;
    private BigDecimal score;
    private LocalDateTime updatedAt;
}
