package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("loan_category")
public class LoanCategory {
    @TableId
    private String code;
    private String name;
    private String description;
    private String iconKey;
    private Integer sort;
    private LocalDateTime updateTime;
}
