package org.example.risklendpro.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_ticket")
public class CsTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long conversationId;
    private String category;
    private String description;
    private Integer involvesMoney;
    private String priority;
    private String bizId;
    private String status;
    private Long handlerId;
    private String result;
    private LocalDateTime createTime;
    private LocalDateTime closeTime;
}
