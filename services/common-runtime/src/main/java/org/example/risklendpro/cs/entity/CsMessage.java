package org.example.risklendpro.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_message")
public class CsMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long userId;
    private String role;
    private String content;
    private String intent;
    private String model;
    private String requestId;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createTime;
}
