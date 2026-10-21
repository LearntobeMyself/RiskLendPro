package org.example.risklendpro.risk.supplement;

import org.example.risklendpro.risk.entity.RiskSupplementMaterial;
import org.example.risklendpro.risk.supplement.FileStorageService;
import org.example.risklendpro.risk.supplement.SupplementMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/admin/supplement")
@PreAuthorize("hasAuthority('risk:approve')")
public class AdminSupplementController {

    /** 仅这些扩展名允许以 inline 方式在浏览器中预览（图片/PDF），其余一律 attachment 下载。 */
    private static final Set<String> INLINE_SAFE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "pdf"
    );

    private static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM_VALUE;

    @Autowired
    private SupplementMaterialService supplementMaterialService;

    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping("/file/{materialId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long materialId,
            @RequestParam(required = false, defaultValue = "false") boolean inline) throws Exception {
        RiskSupplementMaterial material = supplementMaterialService.getById(materialId);
        if (material == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = fileStorageService.resolvePath(material.getStoredPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);

        String filename = material.getOriginalName();
        String extension = extensionOf(filename);
        String contentType = safeContentType(extension, material.getMimeType());
        // 仅图片/PDF 允许 inline 预览，其余强制 attachment，杜绝存储型 XSS
        boolean safeInline = inline && INLINE_SAFE_EXTENSIONS.contains(extension);
        String disposition = buildContentDisposition(safeInline ? "inline" : "attachment", filename);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(parseSafely(contentType))
                .body(resource);
    }

    /** 客户端上传的 MIME 可能含非法字符，解析失败时回退 octet-stream 而非抛 500。 */
    private MediaType parseSafely(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** 仅在白名单扩展名内采信 MIME，否则一律 octet-stream，避免上传时伪造 text/html 导致 XSS。 */
    private String safeContentType(String extension, String declaredMimeType) {
        if (!INLINE_SAFE_EXTENSIONS.contains(extension)) {
            return DEFAULT_CONTENT_TYPE;
        }
        if (declaredMimeType == null || declaredMimeType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        String lower = declaredMimeType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("image/") || "application/pdf".equals(lower)) {
            return declaredMimeType;
        }
        return DEFAULT_CONTENT_TYPE;
    }

    /** 提取小写扩展名（不含点）；无扩展名返回空串。 */
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

    /** RFC 5987 编码 filename*，并提供 ASCII fallback，防止 CRLF/引号注入与非 ASCII 乱码。 */
    private String buildContentDisposition(String type, String filename) {
        String safe = filename == null ? "download" : filename;
        String fallback = safe.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }
}
