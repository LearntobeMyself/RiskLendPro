package org.example.risklendpro.risk.blacklist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.risk.blacklist.BlacklistAddRequest;
import org.example.risklendpro.risk.blacklist.BlacklistAddResponse;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.risk.blacklist.BlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "黑名单同步", description = "向 credit_data_db.blacklist 写入黑名单记录，需永久 API Token 鉴权")
@RestController
@RequestMapping("/sync")
public class BlacklistSyncController {

    @Autowired
    private BlacklistService blacklistService;

    @Operation(
            summary = "写入黑名单记录",
            description = "校验字段后写入 credit_data_db.blacklist，createdAt 由服务端生成，expireAt 默认为永久有效。"
                    + " 请求头：Authorization: Bearer {risk.blacklist-sync.api-token}",
            security = @SecurityRequirement(name = "Authorization")
    )
    @PostMapping("/blacklist")
    public CommonResponse<BlacklistAddResponse> addBlacklist(@RequestBody BlacklistAddRequest request) {
        BlacklistAddResponse response = blacklistService.addBlacklist(request);
        return CommonResponse.success("黑名单写入成功", response);
    }
}
