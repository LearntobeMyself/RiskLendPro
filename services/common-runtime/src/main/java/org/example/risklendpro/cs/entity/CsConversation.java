package org.example.risklendpro.cs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_conversation")
public class CsConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String status;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
