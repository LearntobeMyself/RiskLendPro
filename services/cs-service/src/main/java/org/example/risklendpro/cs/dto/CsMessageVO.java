package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "历史消息视图（不暴露模型与 token 等内部字段）")
public class CsMessageVO {

    @Schema(description = "消息角色：USER/ASSISTANT")
    private String role;

    private String content;

    private String intent;

    private LocalDateTime createTime;
}
