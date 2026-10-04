package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("loan_institution")
public class LoanInstitution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String institutionName;
    private String institutionType;
    private String logoKey;
    private String introduction;
    private Integer enabled;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
