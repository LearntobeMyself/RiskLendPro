package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("loan_image")
public class LoanImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectKey;
    private String imageType;
    private String bizId;
    private Integer sort;
    private String status;
    private String contentType;
    private Long fileSize;
    private Long createBy;
    private LocalDateTime createTime;
}
