package org.example.risklendpro.cs.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.risklendpro.cs.entity.CsAuditLog;
import org.example.risklendpro.cs.mapper.CsAuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客服审计日志：记录敏感拦截、实时数据查询、工单操作等关键动作，供安全追溯。
 * 写入失败仅告警，不阻断主流程。
 */
@Service
public class CsAuditService {

    private static final Logger log = LoggerFactory.getLogger(CsAuditService.class);

    public static final String ACTION_SENSITIVE_BLOCK = "SENSITIVE_BLOCK";
    public static final String ACTION_REALTIME_QUERY = "REALTIME_QUERY";
    public static final String ACTION_TICKET_CREATE = "TICKET_CREATE";
    public static final String ACTION_TICKET_PROCESS = "TICKET_PROCESS";
    public static final String ACTION_TICKET_CLOSE = "TICKET_CLOSE";

    private final CsAuditLogMapper auditLogMapper;

    public CsAuditService(CsAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(Long userId, String action, String targetType, String targetId, String detail) {
        try {
            CsAuditLog entry = new CsAuditLog();
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(truncate(detail, 500));
            entry.setClientIp(currentIp());
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
