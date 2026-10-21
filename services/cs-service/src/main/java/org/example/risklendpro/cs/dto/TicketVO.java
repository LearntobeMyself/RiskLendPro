package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "工单视图")
public class TicketVO {

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
