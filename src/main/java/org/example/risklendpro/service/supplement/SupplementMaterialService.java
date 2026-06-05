package org.example.risklendpro.service.supplement;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.config.SupplementMaterialProperties;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.RiskSupplementMaterial;
import org.example.risklendpro.enums.StatusEnum;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.RiskSupplementMaterialMapper;
import org.example.risklendpro.pojo.dto.SupplementRequirement;
import org.example.risklendpro.utils.EmailUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SupplementMaterialService {

    private static final Logger log = LoggerFactory.getLogger(SupplementMaterialService.class);
    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_REQUIRED = "REQUIRED";
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    @Autowired
    private RiskSupplementMaterialMapper materialMapper;

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private SupplementMaterialProperties properties;

    @Autowired
    private SupplementRequirementResolver requirementResolver;

    @Autowired
    private EmailUtil emailUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setupManualReviewSupplement(RiskAssessment assessment, List<String> riskTags) {
        if (assessment == null || !StatusEnum.MANUAL_REVIEW.getValue().equals(assessment.getStatus())) {
            return;
        }
        List<SupplementRequirement> requirements = requirementResolver.resolve(assessment.getAuditRemark(), riskTags);
        if (!requirementResolver.hasRequiredMaterials(requirements)) {
            assessment.setSupplementStatus(STATUS_NONE);
            assessment.setSupplementRequirements(null);
            return;
        }
        try {
            assessment.setSupplementStatus(STATUS_REQUIRED);
            assessment.setSupplementRequirements(objectMapper.writeValueAsString(requirements));
        } catch (Exception e) {
            log.warn("Serialize supplement requirements failed, applyId={}", assessment.getApplyId(), e);
            assessment.setSupplementStatus(STATUS_NONE);
        }
    }

    public void sendSupplementNoticeEmail(RiskAssessment assessment, List<SupplementRequirement> requirements) {
        if (assessment == null || requirements == null || requirements.isEmpty()) {
            return;
        }
        try {
            emailUtil.sendManualReviewSupplementNotice(
                    assessment.getEmail(),
                    assessment.getName(),
                    requirements,
                    properties.getRetentionDays());
        } catch (Exception e) {
            log.warn("Manual review supplement email failed, applyId={}", assessment.getApplyId(), e);
        }
    }

    public void sendSupplementExpiredEmail(RiskAssessment assessment, List<SupplementRequirement> requirements) {
        if (assessment == null) {
            return;
        }
        try {
            emailUtil.sendSupplementMaterialExpiredNotice(
                    assessment.getEmail(),
                    assessment.getName(),
                    requirements,
                    properties.getRetentionDays());
        } catch (Exception e) {
            log.warn("Supplement expired email failed, applyId={}", assessment.getApplyId(), e);
        }
    }

    public Map<String, Object> getRequirementsForUser(Long userId) {
        RiskAssessment assessment = findLatestManualReviewAssessment(userId);
        if (assessment == null) {
            throw new RuntimeException("当前没有需要补充材料的人工复核申请");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applyId", assessment.getApplyId());
        result.put("status", assessment.getStatus());
        result.put("supplementStatus", assessment.getSupplementStatus());
        List<SupplementRequirement> requirements = parseRequirements(assessment.getSupplementRequirements());
        result.put("requiredMaterials", requirements);
        result.put("uploadedMaterials", listUploadedMaps(assessment.getApplyId(), false));
        result.put("uploadedMaterialTypes", listUploadedTypes(assessment.getApplyId()));
        result.put("supplementDeadline", computeDeadline(requirements));
        return result;
    }

    public List<String> listUploadedTypes(String applyId) {
        return listActiveMaterials(applyId).stream()
                .map(RiskSupplementMaterial::getMaterialType)
                .distinct()
                .collect(Collectors.toList());
    }

    public boolean hasUploadedType(String applyId, String materialType) {
        return listActiveMaterials(applyId).stream()
                .anyMatch(m -> materialType.equals(m.getMaterialType()));
    }

    public String resolveRuleTrigger(RiskAssessment assessment) {
        if (assessment == null) {
            return "NONE";
        }
        return requirementResolver.resolvePrimaryRuleCategory(assessment.getAuditRemark(), null);
    }

    public String resolveRuleGate(List<String> riskTags) {
        return requirementResolver.resolveRuleGate(riskTags);
    }

    @Transactional
    public Map<String, Object> upload(Long userId, String materialType, MultipartFile file, String remark) {
        RiskAssessment assessment = findLatestManualReviewAssessment(userId);
        if (assessment == null) {
            throw new RuntimeException("当前没有可上传材料的人工复核申请");
        }
        if (!userId.equals(assessment.getUserId())) {
            throw new RuntimeException("无权上传该申请的材料");
        }
        String supplementStatus = assessment.getSupplementStatus();
        if (!STATUS_REQUIRED.equals(supplementStatus)) {
            throw new RuntimeException("当前申请不需要上传补充材料");
        }
        List<SupplementRequirement> requirements = parseRequirements(assessment.getSupplementRequirements());
        validateMaterialType(materialType, requirements);
        if (hasUploadedType(assessment.getApplyId(), materialType)) {
            throw new RuntimeException("该类型材料已上传，请勿重复提交");
        }

        try {
            FileStorageService.StoredFile stored = fileStorageService.store(assessment.getApplyId(), file);
            RiskSupplementMaterial row = new RiskSupplementMaterial();
            row.setApplyId(assessment.getApplyId());
            row.setUserId(userId);
            row.setMaterialType(materialType);
            row.setOriginalName(stored.originalName());
            row.setStoredPath(stored.storedPath());
            row.setFileSize(stored.size());
            row.setMimeType(stored.mimeType());
            row.setRemark(remark);
            row.setUploadTime(new Date());
            row.setExpireAt(addDays(new Date(), properties.getRetentionDays()));
            materialMapper.insert(row);

            if (allRequiredUploaded(assessment.getApplyId(), requirements)) {
                assessment.setSupplementStatus(STATUS_SUBMITTED);
            } else {
                assessment.setSupplementStatus(STATUS_REQUIRED);
            }
            riskAssessmentMapper.updateById(assessment);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("materialId", row.getId());
            resp.put("applyId", assessment.getApplyId());
            resp.put("materialType", materialType);
            resp.put("supplementStatus", assessment.getSupplementStatus());
            resp.put("expireAt", row.getExpireAt());
            return resp;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("材料上传失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listByApplyIdForReport(String applyId) {
        return listUploadedMaps(applyId, true);
    }

    public RiskSupplementMaterial getById(Long materialId) {
        return materialMapper.selectById(materialId);
    }

    @Transactional
    public int cleanupExpiredMaterials() {
        List<RiskSupplementMaterial> expired = materialMapper.selectList(
                new QueryWrapper<RiskSupplementMaterial>().lt("expire_at", new Date()));
        if (expired.isEmpty()) {
            return 0;
        }
        Set<String> affectedApplyIds = new HashSet<>();
        for (RiskSupplementMaterial material : expired) {
            fileStorageService.deleteIfExists(material.getStoredPath());
            materialMapper.deleteById(material.getId());
            affectedApplyIds.add(material.getApplyId());
        }
        for (String applyId : affectedApplyIds) {
            handleApplyIdAfterCleanup(applyId);
        }
        return expired.size();
    }

    @Transactional
    public void deleteAllByApplyId(String applyId) {
        List<RiskSupplementMaterial> rows = materialMapper.selectList(
                new QueryWrapper<RiskSupplementMaterial>().eq("apply_id", applyId));
        for (RiskSupplementMaterial row : rows) {
            fileStorageService.deleteIfExists(row.getStoredPath());
            materialMapper.deleteById(row.getId());
        }
    }

    @Transactional
    public void clearSupplementOnFinalApproval(String applyId) {
        deleteAllByApplyId(applyId);
        RiskAssessment assessment = riskAssessmentMapper.selectById(applyId);
        if (assessment != null) {
            assessment.setSupplementStatus(STATUS_NONE);
            assessment.setSupplementRequirements(null);
            riskAssessmentMapper.updateById(assessment);
        }
    }

    public List<SupplementRequirement> parseRequirements(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SupplementRequirement>>() {});
        } catch (Exception e) {
            log.warn("Parse supplement requirements failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Date computeDeadline(List<SupplementRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return null;
        }
        return addDays(new Date(), properties.getRetentionDays());
    }

    private void handleApplyIdAfterCleanup(String applyId) {
        RiskAssessment assessment = riskAssessmentMapper.selectById(applyId);
        if (assessment == null) {
            return;
        }
        if (!StatusEnum.MANUAL_REVIEW.getValue().equals(assessment.getStatus())) {
            return;
        }
        if (!STATUS_SUBMITTED.equals(assessment.getSupplementStatus())
                && !STATUS_REQUIRED.equals(assessment.getSupplementStatus())) {
            return;
        }
        List<SupplementRequirement> requirements = parseRequirements(assessment.getSupplementRequirements());
        if (!requirementResolver.hasRequiredMaterials(requirements)) {
            return;
        }
        assessment.setSupplementStatus(STATUS_REQUIRED);
        riskAssessmentMapper.updateById(assessment);
        sendSupplementExpiredEmail(assessment, requirements);
    }

    private RiskAssessment findLatestManualReviewAssessment(Long userId) {
        return riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .eq("status", StatusEnum.MANUAL_REVIEW.getValue())
                        .orderByDesc("submit_time")
                        .last("LIMIT 1"));
    }

    private List<RiskSupplementMaterial> listActiveMaterials(String applyId) {
        return materialMapper.selectList(
                new QueryWrapper<RiskSupplementMaterial>()
                        .eq("apply_id", applyId)
                        .gt("expire_at", new Date()));
    }

    private List<Map<String, Object>> listUploadedMaps(String applyId, boolean includeDownloadUrl) {
        List<RiskSupplementMaterial> rows = materialMapper.selectList(
                new QueryWrapper<RiskSupplementMaterial>()
                        .eq("apply_id", applyId)
                        .orderByDesc("upload_time"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (RiskSupplementMaterial row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", row.getId());
            m.put("materialType", row.getMaterialType());
            m.put("originalName", row.getOriginalName());
            m.put("fileSize", row.getFileSize());
            m.put("mimeType", row.getMimeType());
            m.put("remark", row.getRemark());
            m.put("uploadTime", row.getUploadTime());
            m.put("expireAt", row.getExpireAt());
            if (includeDownloadUrl) {
                m.put("downloadUrl", "/admin/supplement/file/" + row.getId());
            }
            list.add(m);
        }
        return list;
    }

    private boolean allRequiredUploaded(String applyId, List<SupplementRequirement> requirements) {
        Set<String> requiredCodes = requirements.stream()
                .filter(SupplementRequirement::isRequired)
                .map(SupplementRequirement::getCode)
                .collect(Collectors.toSet());
        if (requiredCodes.isEmpty()) {
            return false;
        }
        List<RiskSupplementMaterial> uploaded = listActiveMaterials(applyId);
        Set<String> uploadedTypes = uploaded.stream()
                .map(RiskSupplementMaterial::getMaterialType)
                .collect(Collectors.toSet());
        return uploadedTypes.containsAll(requiredCodes);
    }

    private void validateMaterialType(String materialType, List<SupplementRequirement> requirements) {
        boolean allowed = requirements.stream().anyMatch(r -> r.getCode().equals(materialType));
        if (!allowed) {
            throw new RuntimeException("不支持的材料类型: " + materialType);
        }
    }

    private Date addDays(Date base, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
}
