package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "工单关单请求")
public class TicketCloseRequest {

    @Schema(description = "处理结果")
    @NotBlank(message = "处理结果不能为空")
    private String result;
}
