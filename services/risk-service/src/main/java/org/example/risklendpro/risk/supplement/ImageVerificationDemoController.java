package org.example.risklendpro.risk.supplement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.risk.supplement.dto.ImageVerificationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "图片核验 Demo", description = "风控补充材料图片识别 / AI 生成检测 / 篡改检测演示接口，匿名可访问")
@RestController
@RequestMapping("/demo/image-verify")
public class ImageVerificationDemoController {

    @Autowired
    private ImageVerificationService imageVerificationService;

    @Operation(summary = "一键核验任意图片", description = "上传一张图片，返回 OCR 文字识别 + AI 生成检测 + 篡改检测的综合结果")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ImageVerificationResult> verify(
            @Parameter(description = "要核验的图片文件（jpg/png）", required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return CommonResponse.fail(400, "请选择要上传的图片文件");
        }
        ImageVerificationResult result = imageVerificationService.verifyAll(file, "demo-" + System.currentTimeMillis());
        return CommonResponse.success("核验完成", result);
    }
}
