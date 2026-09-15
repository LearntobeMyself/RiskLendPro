package org.example.risklendpro.risk.blacklist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "黑名单写入响应")
public class BlacklistAddResponse {
    @Schema(description = "写入记录主键 ID")
    private Long id;

    @Schema(description = "数据创建时间")
    private Date createdAt;
}
