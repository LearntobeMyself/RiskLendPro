package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理端客服工作台会话视图")
@Data
public class AdminConversationVO {

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "会话状态：OPEN/TRANSFERRED/AGENT/CLOSED")
    private String status;

    @Schema(description = "最近一条消息摘要")
    private String lastMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
