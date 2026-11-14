package org.example.risklendpro.risk.supplement;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.example.risklendpro.risk.supplement.dto.ImageVerificationResult;
import org.example.risklendpro.risk.supplement.dto.MetadataRiskResult;
import org.example.risklendpro.risk.supplement.dto.OcrResult;
import org.example.risklendpro.risk.supplement.dto.TamperCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_WARN = "WARN";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private static final String ALGORITHM = "EXIF_METADATA_RULES";
    private static final String NOTICE =
            "本项为基于 EXIF 元数据的图片安全检查（不使用视觉模型、不分析像素），"
                    + "仅作风险提示与留痕；改写或擦除 EXIF 即可绕过，不能据此断言图片是否由 AI 生成。";

    /** 命中 AI 工具自报的 Software 字段，属确定性证据，直接阻断。 */
    private static final double RISK_AI_TOOL = 0.95;
    /** 存在编辑软件痕迹（可能只是裁剪调色，单独不阻断）。 */
    private static final double RISK_EDITED = 0.85;
    /** 无任何 EXIF 的非 PNG 图片：高概率被二次处理，仅提示。 */
    private static final double RISK_NO_EXIF = 0.70;
    /** EXIF 存在但缺拍摄设备信息。 */
    private static final double RISK_NO_DEVICE = 0.60;
    /** PNG 无 EXIF（截图、导出图常见），信号很弱。 */
    private static final double RISK_PNG_NO_EXIF = 0.35;

    public ImageVerificationService(SupplementMaterialProperties properties) {
        this.properties = properties;
    }

    public ImageVerificationResult verifyAll(MultipartFile file, String supplementId) {
        Path tempFile = null;
        try {
            String filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
            tempFile = Files.createTempFile("risklendpro_", "_" + filename);
            // 必须用流复制而不是 MultipartFile.transferTo：transferTo 在容器实现上
            // 可能把上传临时文件移走，后续保存材料时就读不到了。
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return verifyAll(tempFile, supplementId, file.getContentType());
        } catch (Exception e) {
            log.warn("图片校验临时文件处理失败: {}", e.getMessage());
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
        ExifSnapshot exif = readExif(imagePath);
        MetadataRiskResult metadataRisk = checkExifRisk(exif);
        TamperCheckResult tamperCheck = detectTampered(exif);

        String status = computeStatus(metadataRisk, tamperCheck);
        boolean blocked = STATUS_FAIL.equals(status);
        metadataRisk.setBlocked(blocked);

        return ImageVerificationResult.builder()
                .supplementId(supplementId)
                .ocr(ocr)
                .metadataRisk(metadataRisk)
                .tamper(tamperCheck)
                .status(status)
                .blocked(blocked)
                .notice(NOTICE)
                .detail(buildDetail(ocr, metadataRisk, tamperCheck, status))
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

    /** EXIF 只读快照，一次解析供元数据风险与篡改提示共用。 */
    record ExifSnapshot(boolean hasExif, boolean isPng,
                                String software, String make, String model) {
        boolean captureDevicePresent() {
            return notBlank(make) || notBlank(model);
        }
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private ExifSnapshot readExif(Path imagePath) {
        boolean hasExif = false;
        boolean isPng = false;
        String software = null;
        String make = null;
        String model = null;

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
                hasExif = true;
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
                hasExif = true;
            }
        } catch (Exception e) {
            log.debug("EXIF 元数据读取异常: {}", e.getMessage());
        }
        return new ExifSnapshot(hasExif, isPng, software, make, model);
    }

    /**
     * 基于 EXIF 的元数据风险评估。
     *
     * <p>唯一可以定性的是 Software 字段自报为 AI 生成工具（工具会主动写入），
     * 其余信号（无 EXIF、缺设备信息、PNG 无 EXIF）都只能作为风险提示，
     * 因为压缩、微信传输、截图、手动擦除都会造成同样的结果。</p>
     */
    MetadataRiskResult checkExifRisk(ExifSnapshot exif) {
        if (exif.software() != null) {
            String softwareLower = exif.software().toLowerCase();
            for (String kw : AI_SOFTWARE_KEYWORDS) {
                if (softwareLower.contains(kw)) {
                    return metadataResult(true, RISK_AI_TOOL, "HIGH", exif.captureDevicePresent(),
                            "EXIF Software 字段标注了 AI 生成工具：" + exif.software()
                                    + "（该字段由导出工具写入，属确定性证据）");
                }
            }
        }

        if (!exif.hasExif()) {
            if (exif.isPng()) {
                return metadataResult(true, RISK_PNG_NO_EXIF, "LOW", false,
                        "图片无 EXIF 元数据（PNG 截图/导出图常见，无法判定来源）");
            }
            return metadataResult(true, RISK_NO_EXIF, "MEDIUM", false,
                    "图片无 EXIF 元数据（可能经二次处理、压缩或擦除，来源不可验证）");
        }

        if (!exif.captureDevicePresent()) {
            return metadataResult(true, RISK_NO_DEVICE, "MEDIUM", false,
                    "EXIF 存在但缺少拍摄设备信息（Make / Model 均为空）");
        }

        StringBuilder reason = new StringBuilder("检测到拍摄设备信息");
        if (exif.make() != null) reason.append("，Make=").append(exif.make());
        if (exif.model() != null) reason.append("，Model=").append(exif.model());
        if (exif.software() != null) reason.append("，Software=").append(exif.software());
        return metadataResult(false, 0.0, "NONE", true, reason.toString());
    }

    private MetadataRiskResult metadataResult(boolean suspicious, double riskScore, String level,
                                             boolean captureDevicePresent, String reason) {
        return MetadataRiskResult.builder()
                .suspicious(suspicious)
                .riskScore(riskScore)
                .level(level)
                .captureDevicePresent(captureDevicePresent)
                .reason(reason)
                .algorithm(ALGORITHM)
                .visualModelUsed(false)
                .build();
    }

    /**
     * 编辑痕迹提示：仅依据 EXIF Software 字段。
     *
     * <p>已移除旧版"文件大于 512KB 且为 JPEG 即判篡改（55%）"的规则——
     * 手机原图普遍 2~5MB，那条规则会对正常照片稳定误报。</p>
     */
    TamperCheckResult detectTampered(ExifSnapshot exif) {
        if (exif.software() != null) {
            String softwareLower = exif.software().toLowerCase();
            for (String kw : EDITION_SOFTWARE_KEYWORDS) {
                if (softwareLower.contains(kw)) {
                    return TamperCheckResult.builder()
                            .tampered(true)
                            .confidence(RISK_EDITED)
                            .reason("EXIF Software 字段显示经编辑软件处理: " + exif.software())
                            .build();
                }
            }
        }
        return TamperCheckResult.builder()
                .tampered(false)
                .confidence(0.0)
                .reason("EXIF 未显示编辑软件痕迹")
                .build();
    }

    /**
     * 状态判定（EXIF 信号分层）：
     * <ul>
     *   <li>FAIL：EXIF Software 自报 AI 生成工具；或既有编辑软件痕迹又无拍摄设备信息 —— 拒绝保存</li>
     *   <li>WARN：无 EXIF、缺设备信息、PNG 无 EXIF、有拍摄来源但经编辑 —— 允许保存，提示复核</li>
     *   <li>PASS：存在拍摄设备信息且无编辑痕迹</li>
     * </ul>
     */
    String computeStatus(MetadataRiskResult metadata, TamperCheckResult tamper) {
        if (metadata.getRiskScore() >= RISK_AI_TOOL) {
            return STATUS_FAIL;
        }
        if (tamper.isTampered() && !metadata.isCaptureDevicePresent()) {
            return STATUS_FAIL;
        }
        if (tamper.isTampered() || metadata.getRiskScore() >= RISK_PNG_NO_EXIF) {
            return STATUS_WARN;
        }
        return STATUS_PASS;
    }

    private String buildDetail(OcrResult ocr, MetadataRiskResult metadata, TamperCheckResult tamper, String status) {
        StringBuilder sb = new StringBuilder();
        sb.append("检测方式: EXIF 元数据规则（非视觉模型）; ");
        if (ocr.isSuccess()) {
            sb.append("OCR 提取字数: ").append(ocr.getText().length()).append("; ");
        } else {
            sb.append("OCR 未通过: ").append(ocr.getError()).append("; ");
        }
        sb.append("元数据风险分: ").append(String.format("%.0f%%", metadata.getRiskScore() * 100))
                .append("（").append(metadata.getLevel()).append("）; ");
        sb.append("编辑痕迹: ").append(tamper.isTampered() ? "有" : "无").append("; ");
        sb.append("处置: ").append(switch (status) {
            case STATUS_FAIL -> "已拒绝保存";
            case STATUS_WARN -> "需人工复核";
            case STATUS_PASS -> "通过";
            default -> "未执行";
        });
        return sb.toString();
    }

    private ImageVerificationResult buildSkipResult(String supplementId, String msg) {
        OcrResult ocr = OcrResult.builder().success(false).text("").error(msg).build();
        MetadataRiskResult metadata = metadataResult(false, 0.0, "NONE", false, "元数据未检查");
        TamperCheckResult tamper = TamperCheckResult.builder().tampered(false).confidence(0).reason("未检查").build();
        return ImageVerificationResult.builder()
                .supplementId(supplementId)
                .ocr(ocr).metadataRisk(metadata).tamper(tamper)
                .status(STATUS_SKIPPED)
                .blocked(false)
                .notice(NOTICE)
                .detail(msg)
                .build();
    }
}
