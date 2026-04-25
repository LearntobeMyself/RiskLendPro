package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("vintage_data")
public class VintageData {
    private Long id;
    private String month;
    @TableField("disbursed_amount")
    private BigDecimal disbursedAmount;
    @TableField("m1_rate")
    private BigDecimal m1Rate;
    @TableField("m2_rate")
    private BigDecimal m2Rate;
    @TableField("m3_rate")
    private BigDecimal m3Rate;
    @TableField("create_time")
    private Date createTime;
}