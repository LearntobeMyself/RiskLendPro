package org.example.risklendpro.risk.supplement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.risk.supplement.dto.ImageVerificationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * 图片核验演示接口。
 *
 * <p>默认不注册：只有 {@code security.demo.image-verify-enabled=true} 时才装配该 Bean，
 * 同时 SecurityConfig 的白名单与 JwtFilter 的放行也由同一个开关控制，
 * 避免上线后留下一个免鉴权的重计算入口。</p>
 *
 * <p>打开后仍有三道约束：按客户端 IP 的分钟级限流、仅允许图片扩展名、单文件大小受
 * {@code risk.supplement.max-file-size-mb} 限制。</p>
 */
@Tag(name = "图片核验 Demo", description = "风控补充材料图片识别 / AI 生成检测 / 篡改检测演示接口（默认关闭，需显式开启且匿名访问）")
@RestController
@RequestMapping("/demo/image-verify")
@ConditionalOnProperty(prefix = "security.demo", name = "image-verify-enabled", havingValue = "true")
public class ImageVerificationDemoController {

    /** 演示接口只接受图片；补充材料里允许的 pdf 不在此列。 */
    private static final Set<String> DEMO_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final ImageVerificationService imageVerificationService;
    private final DemoRequestRateLimiter rateLimiter;
    private final SupplementMaterialProperties properties;

    public ImageVerificationDemoController(ImageVerificationService imageVerificationService,
                                           DemoRequestRateLimiter rateLimiter,
                                           SupplementMaterialProperties properties) {
        this.imageVerificationService = imageVerificationService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Operation(summary = "一键核验任意图片", description = "上传一张图片，返回 OCR 文字识别 + AI 生成检测 + 篡改检测的综合结果")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ImageVerificationResult> verify(
            @Parameter(description = "要核验的图片文件（jpg/png）", required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {

        if (!rateLimiter.tryAcquire(clientKey(request))) {
            return CommonResponse.fail(429, "请求过于频繁，每分钟最多 "
                    + rateLimiter.getLimitPerMinute() + " 次，请稍后再试");
        }

        if (file == null || file.isEmpty()) {
            return CommonResponse.fail(400, "请选择要上传的图片文件");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!DEMO_IMAGE_EXTENSIONS.contains(extension)) {
            return CommonResponse.fail(415, "演示接口仅支持 jpg/jpeg/png 图片");
        }
        if (!properties.allowedExtensionList().contains(extension)) {
            return CommonResponse.fail(415, "当前服务未开放 " + extension + " 类型");
        }

        long maxBytes = properties.maxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            return CommonResponse.fail(413, "图片超过大小限制（最大 " + properties.getMaxFileSizeMb() + "MB）");
        }

        ImageVerificationResult result = imageVerificationService.verifyAll(file, "demo-" + System.currentTimeMillis());
        return CommonResponse.success("核验完成", result);
    }

    /**
     * 限流用的客户端标识：优先取网关注入的 X-Forwarded-For 首个地址，
     * 直连时退化为 remoteAddr，都拿不到时归入 unknown 桶（仍受限流保护）。
     */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
