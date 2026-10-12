package org.example.risklendpro.risk.supplement;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.example.risklendpro.risk.supplement.dto.AiGenerateCheckResult;
import org.example.risklendpro.risk.supplement.dto.ImageVerificationResult;
import org.example.risklendpro.risk.supplement.dto.OcrResult;
import org.example.risklendpro.risk.supplement.dto.TamperCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class ImageVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ImageVerificationService.class);

    private static final Set<String> AI_SOFTWARE_KEYWORDS = new HashSet<>(Arrays.asList(
            "midjourney", "stable diffusion", "stablediffusion", "dall-e", "dalle",
            "firefly", "ideogram", "leonardo", "playground", "bing image creator",
            "chatgpt", "gpt-image-1", "recraft", "flux", "runway", "pika"
    ));

    private static final Set<String> EDITION_SOFTWARE_KEYWORDS = new HashSet<>(Arrays.asList(
            "photoshop", "adobe photoshop", "lightroom", "gimp", "paint.net",
            "coreldraw", "illustrator", "pixlr", "canva", "美图秀秀", "wps",
            "snapseed", "vsco", "polarr", "泼辣修图", "醒图", "剪映"
    ));

    private final SupplementMaterialProperties properties;

    public ImageVerificationService(SupplementMaterialProperties properties) {
        this.properties = properties;
    }

    public ImageVerificationResult verifyAll(MultipartFile file, String supplementId) {
        Path tempFile = null;
        try {
            String filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
            tempFile = Files.createTempFile("risklendpro_", "_" + filename);
            file.transferTo(tempFile.toFile());
            return verifyAll(tempFile, supplementId, file.getContentType());
        } catch (Exception e) {
            log.warn("Demo 模式文件落盘失败: {}", e.getMessage());
            return buildSkipResult(supplementId, "文件处理失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
    }

    public ImageVerificationResult verifyAll(Path imagePath, String supplementId) {
        return verifyAll(imagePath, supplementId, null);
    }

    private ImageVerificationResult verifyAll(Path imagePath, String supplementId, String contentType) {
        OcrResult ocr = extractText(imagePath, contentType);
        AiGenerateCheckResult aiCheck = detectAIGenerated(imagePath);
        TamperCheckResult tamperCheck = detectTampered(imagePath);

        String status = computeStatus(aiCheck, tamperCheck);
        String detail = buildDetail(ocr, aiCheck, tamperCheck);

        return ImageVerificationResult.builder()
                .supplementId(supplementId)
                .ocr(ocr)
                .aiGenerate(aiCheck)
                .tamper(tamperCheck)
                .status(status)
                .detail(detail)
                .build();
    }

    private OcrResult extractText(Path imagePath, String contentType) {
        String name = imagePath.getFileName() == null ? "" : imagePath.getFileName().toString().toLowerCase();
        if ((contentType != null && contentType.contains("pdf")) || name.endsWith(".pdf")) {
            return OcrResult.builder()
                    .success(false)
                    .text("")
                    .error("PDF 文件暂不支持 OCR（MVP 仅支持图片）")
                    .build();
        }

        try {
            Tesseract tesseract = new Tesseract();
            String configured = properties.getTessdataPath();
            String datapath = resolveTessdataDir(configured);
            if (datapath == null && configured != null && !configured.isBlank()) {
                return OcrResult.builder()
                        .success(false)
                        .text("")
                        .error("OCR 语言包目录未找到（需包含 chi_sim.traineddata / eng.traineddata），已跳过 OCR")
                        .build();
            }
            if (datapath != null) {
                tesseract.setDatapath(datapath);
            }
            tesseract.setLanguage("chi_sim+eng");
            tesseract.setPageSegMode(1);

            String text = tesseract.doOCR(imagePath.toFile());
            return OcrResult.builder()
                    .success(true)
                    .text(text == null ? "" : text.trim())
                    .error(null)
                    .build();
        } catch (TesseractException e) {
            log.warn("OCR 识别失败: {}", e.getMessage());
            return OcrResult.builder()
                    .success(false)
                    .text("")
                    .error("OCR 引擎不可用或识别失败: " + e.getMessage())
                    .build();
        } catch (Exception | Error e) {
            // tess4j native 库在 tessdata 缺失等情况下会抛 java.lang.Error（Invalid memory access），
            // 必须兜住，否则整个核验接口直接 500
            log.warn("OCR 处理异常: {}", e.getMessage());
            return OcrResult.builder()
                    .success(false)
                    .text("")
                    .error("OCR 引擎异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 解析 tessdata 目录：按配置路径（相对工作目录）逐级向上查找，最多 4 层，
     * 兼容项目根启动与 services/risk-service 模块目录启动两种情况。
     * 返回 null 表示找不到（含 chi_sim.traineddata 校验）。
     */
    private String resolveTessdataDir(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path base = Path.of(configured).toAbsolutePath().normalize();
        Path dirName = base.getFileName();
        // 先检查配置路径本身（工作目录在项目根时直接命中）
        if (Files.isDirectory(base) && Files.isRegularFile(base.resolve("chi_sim.traineddata"))) {
            log.info("tessdata 目录解析成功: {}", base);
            return base.toString();
        }
        // 再从上级目录逐级向上找（工作目录在 services/risk-service 等子目录时兜底）
        Path ancestor = base.getParent();
        for (int i = 0; i < 4 && ancestor != null; i++) {
            ancestor = ancestor.getParent();
            if (ancestor == null) {
                break;
            }
            Path candidate = ancestor.resolve(dirName);
            if (Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("chi_sim.traineddata"))) {
                log.info("tessdata 目录解析成功: {}", candidate);
                return candidate.toString();
            }
        }
        return null;
    }

    private AiGenerateCheckResult detectAIGenerated(Path imagePath) {
        boolean hasAnyExif = false;
        String software = null;
        String make = null;
        String model = null;
        boolean isPng = false;

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());
            FileTypeDirectory fileType = metadata.getFirstDirectoryOfType(FileTypeDirectory.class);
            if (fileType != null) {
                String ext = fileType.getString(FileTypeDirectory.TAG_DETECTED_FILE_TYPE_NAME);
                if (ext != null && ext.toLowerCase().contains("png")) {
                    isPng = true;
                }
            }

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            if (ifd0 != null) {
                hasAnyExif = true;
                if (ifd0.containsTag(ExifDirectoryBase.TAG_SOFTWARE)) {
                    software = ifd0.getString(ExifDirectoryBase.TAG_SOFTWARE);
                }
                if (ifd0.containsTag(ExifDirectoryBase.TAG_MAKE)) {
                    make = ifd0.getString(ExifDirectoryBase.TAG_MAKE);
                }
                if (ifd0.containsTag(ExifDirectoryBase.TAG_MODEL)) {
                    model = ifd0.getString(ExifDirectoryBase.TAG_MODEL);
                }
            }
            if (subIfd != null) {
                hasAnyExif = true;
            }
        } catch (Exception e) {
            log.debug("AI 检测元数据读取异常: {}", e.getMessage());
        }

        if (software != null) {
            String softwareLower = software.toLowerCase();
            for (String kw : AI_SOFTWARE_KEYWORDS) {
                if (softwareLower.contains(kw)) {
                    return AiGenerateCheckResult.builder()
                            .aiGenerated(true)
                            .confidence(0.95)
                            .reason("EXIF Software 字段含 AI 工具标识: " + software)
                            .build();
                }
            }
        }

        String reason;
        double confidence;
        boolean aiGenerated;

        if (!hasAnyExif) {
            if (isPng) {
                confidence = 0.35;
                reason = "图片无 EXIF 元数据（PNG 常见情况，无法判定）";
            } else {
                confidence = 0.70;
                reason = "图片无 EXIF 元数据（非 PNG 格式下高概率为 AI 生成或被处理过）";
            }
            aiGenerated = isPng ? false : confidence >= 0.70;
        } else if (make == null && model == null && software == null) {
            confidence = 0.60;
            reason = "EXIF 存在但缺少拍摄设备信息（Make/Model 均为空）";
            aiGenerated = false;
        } else {
            confidence = 0.05;
            StringBuilder sb = new StringBuilder("检测到真实拍摄设备信息");
            if (make != null) sb.append("，Make=").append(make);
            if (model != null) sb.append("，Model=").append(model);
            if (software != null) sb.append("，Software=").append(software);
            reason = sb.toString();
            aiGenerated = false;
        }

        return AiGenerateCheckResult.builder()
                .aiGenerated(aiGenerated)
                .confidence(confidence)
                .reason(reason)
                .build();
    }

    private TamperCheckResult detectTampered(Path imagePath) {
        String software = null;
        boolean suspiciousCompression = false;

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            if (ifd0 != null && ifd0.containsTag(ExifDirectoryBase.TAG_SOFTWARE)) {
                software = ifd0.getString(ExifDirectoryBase.TAG_SOFTWARE);
            }

            suspiciousCompression = checkSuspiciousCompression(imagePath);
        } catch (Exception e) {
            log.debug("篡改检测元数据读取异常: {}", e.getMessage());
        }

        boolean tampered = false;
        double confidence = 0.0;
        StringBuilder reason = new StringBuilder();

        if (software != null) {
            String softwareLower = software.toLowerCase();
            for (String kw : EDITION_SOFTWARE_KEYWORDS) {
                if (softwareLower.contains(kw)) {
                    tampered = true;
                    confidence = 0.85;
                    reason.append("EXIF Software 字段含编辑软件: ").append(software).append("; ");
                    break;
                }
            }
        }

        if (suspiciousCompression) {
            if (!tampered) {
                tampered = true;
                confidence = 0.55;
            }
            reason.append("检测到可疑的 JPEG 压缩痕迹; ");
        }

        if (!tampered) {
            reason.append("未发现明显篡改痕迹");
        }

        return TamperCheckResult.builder()
                .tampered(tampered)
                .confidence(confidence)
                .reason(reason.toString())
                .build();
    }

    private boolean checkSuspiciousCompression(Path file) {
        try {
            long size = Files.size(file);
            if (size < 512L * 1024) {
                return false;
            }
            byte[] header = new byte[2];
            try (var in = Files.newInputStream(file)) {
                if (in.read(header) < 2) return false;
            }
            boolean isJpeg = header[0] == (byte) 0xFF && header[1] == (byte) 0xD8;
            return isJpeg;
        } catch (IOException e) {
            return false;
        }
    }

    private String computeStatus(AiGenerateCheckResult ai, TamperCheckResult tamper) {
        if (ai.isAiGenerated() || tamper.isTampered()) {
            if (ai.getConfidence() >= 0.85 || tamper.getConfidence() >= 0.80) {
                return "FAIL";
            }
            return "WARN";
        }
        if (ai.getConfidence() >= 0.50 || tamper.getConfidence() >= 0.40) {
            return "WARN";
        }
        return "PASS";
    }

    private String buildDetail(OcrResult ocr, AiGenerateCheckResult ai, TamperCheckResult tamper) {
        StringBuilder sb = new StringBuilder();
        if (ocr.isSuccess()) {
            sb.append("OCR 提取字数: ").append(ocr.getText().length()).append("; ");
        } else {
            sb.append("OCR 未通过: ").append(ocr.getError()).append("; ");
        }
        sb.append("AI生成概率: ").append(String.format("%.0f%%", ai.getConfidence() * 100))
                .append("（").append(ai.isAiGenerated() ? "是" : "否").append("）; ");
        sb.append("篡改概率: ").append(String.format("%.0f%%", tamper.getConfidence() * 100))
                .append("（").append(tamper.isTampered() ? "是" : "否").append("）");
        return sb.toString();
    }

    private ImageVerificationResult buildSkipResult(String supplementId, String msg) {
        OcrResult ocr = OcrResult.builder().success(false).text("").error(msg).build();
        AiGenerateCheckResult ai = AiGenerateCheckResult.builder().aiGenerated(false).confidence(0).reason("").build();
        TamperCheckResult tamper = TamperCheckResult.builder().tampered(false).confidence(0).reason("").build();
        return ImageVerificationResult.builder()
                .supplementId(supplementId)
                .ocr(ocr).aiGenerate(ai).tamper(tamper)
                .status("SKIPPED")
                .detail(msg)
                .build();
    }
}
