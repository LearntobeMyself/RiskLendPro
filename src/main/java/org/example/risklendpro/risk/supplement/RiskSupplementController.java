package org.example.risklendpro.risk.supplement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.risk.supplement.SupplementMaterialService;
import org.example.risklendpro.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Tag(name = "补充材料模块", description = "人工复核补充材料上传与查询")
@RestController
@RequestMapping("/risk/assessment/supplement")
public class RiskSupplementController {

    @Autowired
    private SupplementMaterialService supplementMaterialService;

    @Operation(summary = "查询需补充材料清单", description = "返回当前用户最新人工复核单的材料要求与已上传列表")
    @GetMapping("/requirements")
    public CommonResponse<Map<String, Object>> requirements(HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        return CommonResponse.success("查询成功", supplementMaterialService.getRequirementsForUser(userId));
    }

    @Operation(summary = "上传补充材料", description = "multipart/form-data: materialType + file，可选 remark")
    @PostMapping("/upload")
    public CommonResponse<Map<String, Object>> upload(
            @RequestParam String materialType,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String remark,
            HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        Map<String, Object> result = supplementMaterialService.upload(userId, materialType, file, remark);
        return CommonResponse.success("上传成功", result);
    }
}
