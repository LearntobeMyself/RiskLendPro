package org.example.risklendpro.risk.supplement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.risk.supplement.dto.ImageVerificationResult;
import org.example.risklendpro.risk.supplement.dto.MetadataRiskResult;
import org.example.risklendpro.risk.supplement.dto.TamperCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXIF 元数据风险判定策略。
 *
 * <p>这里锁定的是"什么情况才允许阻断上传"，避免再次出现
 * "AI 判定为 true 但材料照样提交"或"手机原图被误判篡改"两类问题。</p>
 */
class ExifRiskPolicyTest {

    private final ImageVerificationService service =
            new ImageVerificationService(new SupplementMaterialProperties());

    private String statusOf(ImageVerificationService.ExifSnapshot exif) {
        MetadataRiskResult metadata = service.checkExifRisk(exif);
        TamperCheckResult tamper = service.detectTampered(exif);
        return service.computeStatus(metadata, tamper);
    }

    @Test
    @DisplayName("EXIF Software 自报 AI 工具：直接 FAIL 并阻断")
    void aiToolSoftwareBlocks() {
        var exif = new ImageVerificationService.ExifSnapshot(
                true, false, "Midjourney v6", null, null);
        MetadataRiskResult metadata = service.checkExifRisk(exif);
        assertTrue(metadata.isSuspicious());
        assertEquals(0.95, metadata.getRiskScore(), 0.001);
        assertEquals("HIGH", metadata.getLevel());
        assertEquals("FAIL", statusOf(exif));
    }

    @Test
    @DisplayName("JPEG 无 EXIF：只提示不阻断（微信压缩/截图都会这样）")
    void jpegWithoutExifOnlyWarns() {
        var exif = new ImageVerificationService.ExifSnapshot(false, false, null, null, null);
        assertEquals(0.70, service.checkExifRisk(exif).getRiskScore(), 0.001);
        assertEquals("WARN", statusOf(exif));
    }

    @Test
    @DisplayName("PNG 无 EXIF：弱信号，WARN")
    void pngWithoutExifOnlyWarns() {
        var exif = new ImageVerificationService.ExifSnapshot(false, true, null, null, null);
        assertEquals(0.35, service.checkExifRisk(exif).getRiskScore(), 0.001);
        assertEquals("WARN", statusOf(exif));
    }

    @Test
    @DisplayName("真实相机照片（Canon EXIF）：PASS，风险分 0")
    void realCameraPhotoPasses() {
        var exif = new ImageVerificationService.ExifSnapshot(
                true, false, null, "Canon", "Canon EOS 80D");
        MetadataRiskResult metadata = service.checkExifRisk(exif);
        assertFalse(metadata.isSuspicious());
        assertEquals(0.0, metadata.getRiskScore(), 0.001);
        assertTrue(metadata.isCaptureDevicePresent());
        assertEquals("PASS", statusOf(exif));
    }

    @Test
    @DisplayName("有拍摄设备但经 GIMP 编辑：WARN，不阻断")
    void editedWithDeviceInfoWarnsNotBlocked() {
        var exif = new ImageVerificationService.ExifSnapshot(
                true, false, "GIMP 2.10", "Canon", "Canon EOS 80D");
        TamperCheckResult tamper = service.detectTampered(exif);
        assertTrue(tamper.isTampered());
        assertEquals(0.85, tamper.getConfidence(), 0.001);
        assertEquals("WARN", statusOf(exif));
    }

    @Test
    @DisplayName("无拍摄来源又有编辑软件：FAIL，阻断")
    void editedWithoutDeviceInfoBlocks() {
        var exif = new ImageVerificationService.ExifSnapshot(
                true, false, "Adobe Photoshop 25.0", null, null);
        assertEquals("FAIL", statusOf(exif));
    }

    @Test
    @DisplayName("旧版“>512KB 即篡改”规则已移除：大图且无编辑软件不再误报")
    void largeFileNoLongerCountedAsTampered() {
        var exif = new ImageVerificationService.ExifSnapshot(
                true, false, null, "Apple", "iPhone 15");
        assertFalse(service.detectTampered(exif).isTampered());
        assertEquals("PASS", statusOf(exif));
    }

    @Test
    @DisplayName("返回结构：声明算法与未用视觉模型，同时保留旧字段兼容 App")
    void resultDeclaresAlgorithmAndKeepsLegacyFields() throws Exception {
        var exif = new ImageVerificationService.ExifSnapshot(false, false, null, null, null);
        ImageVerificationResult result = ImageVerificationResult.builder()
                .supplementId("T-1")
                .metadataRisk(service.checkExifRisk(exif))
                .tamper(service.detectTampered(exif))
                .status("WARN")
                .blocked(false)
                .notice("EXIF only")
                .build();

        String json = new ObjectMapper().writeValueAsString(result);
        assertTrue(json.contains("\"algorithm\":\"EXIF_METADATA_RULES\""), json);
        assertTrue(json.contains("\"visualModelUsed\":false"), json);
        assertTrue(json.contains("\"aiGenerate\""), "旧字段 aiGenerate 必须保留: " + json);
        assertTrue(json.contains("\"confidence\""), "旧字段 confidence 必须保留: " + json);
        assertTrue(json.contains("\"metadataRisk\""), json);
    }
}
