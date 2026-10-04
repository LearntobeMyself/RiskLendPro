package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("loan_product_tag")
public class LoanProductTag {
    @TableId(value = "product_id", type = IdType.INPUT)
    private Long productId;
    private Long tagId;
}
