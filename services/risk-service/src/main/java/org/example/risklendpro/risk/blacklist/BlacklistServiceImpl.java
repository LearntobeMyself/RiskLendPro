package org.example.risklendpro.risk.blacklist;

import org.example.risklendpro.risk.credit.Blacklist;
import org.example.risklendpro.risk.credit.mapper.BlacklistMapper;
import org.example.risklendpro.risk.blacklist.BlacklistAddRequest;
import org.example.risklendpro.risk.blacklist.BlacklistAddResponse;
import org.example.risklendpro.risk.blacklist.BlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Date;
import java.util.Set;

@Service
public class BlacklistServiceImpl implements BlacklistService {

    private static final Set<String> VALID_RISK_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");

    @Autowired
    private BlacklistMapper blacklistMapper;

    @Override
    @Transactional(transactionManager = "creditTransactionManager")
    public BlacklistAddResponse addBlacklist(BlacklistAddRequest request) {
        String name = trimToNull(request.getName());
        if (name == null) {
            throw new RuntimeException("name 不能为空");
        }
        if (name.length() > 100) {
            throw new RuntimeException("name 长度不能超过 100");
        }

        String areaCode = trimToNull(request.getAreaCode());
        Integer birthYear = request.getBirthYear();
        if (birthYear != null) {
            int currentYear = Year.now().getValue();
            if (birthYear < 1900 || birthYear > currentYear) {
                throw new RuntimeException("birthYear 必须在 1900 到 " + currentYear + " 之间");
            }
        }

        String caseNo = trimToNull(request.getCaseNo());
        if (caseNo != null && caseNo.length() > 50) {
            throw new RuntimeException("caseNo 长度不能超过 50");
        }

        String courtName = trimToNull(request.getCourtName());
        if (courtName != null && courtName.length() > 100) {
            throw new RuntimeException("courtName 长度不能超过 100");
        }

        String dutyStatus = trimToNull(request.getDutyStatus());
        if (dutyStatus != null && dutyStatus.length() > 50) {
            throw new RuntimeException("dutyStatus 长度不能超过 50");
        }

        String behaviorDetails = trimToNull(request.getBehaviorDetails());
        if (behaviorDetails != null && behaviorDetails.length() > 500) {
            throw new RuntimeException("behaviorDetails 长度不能超过 500");
        }

        String riskLevel = resolveRiskLevel(trimToNull(request.getRiskLevel()), dutyStatus);

        if (blacklistMapper.countByUniqueKey(name, areaCode, birthYear) > 0) {
            throw new RuntimeException("该黑名单记录已存在");
        }

        Date createdAt = new Date();
        Blacklist record = new Blacklist();
        record.setName(name);
        record.setAreaCode(areaCode);
        record.setBirthYear(birthYear);
        record.setCaseNo(caseNo);
        record.setCourtName(courtName);
        record.setDutyStatus(dutyStatus);
        record.setBehaviorDetails(behaviorDetails);
        record.setRiskLevel(riskLevel);
        record.setCreatedAt(createdAt);
        record.setExpireAt(null);

        blacklistMapper.insert(record);

        BlacklistAddResponse response = new BlacklistAddResponse();
        response.setId(record.getId());
        response.setCreatedAt(createdAt);
        return response;
    }

    private String resolveRiskLevel(String riskLevel, String dutyStatus) {
        if (riskLevel != null) {
            String normalized = riskLevel.toUpperCase();
            if (!VALID_RISK_LEVELS.contains(normalized)) {
                throw new RuntimeException("riskLevel 必须是 HIGH、MEDIUM 或 LOW");
            }
            return normalized;
        }
        if ("全部未履行".equals(dutyStatus)) {
            return "HIGH";
        }
        if ("部分未履行".equals(dutyStatus)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
