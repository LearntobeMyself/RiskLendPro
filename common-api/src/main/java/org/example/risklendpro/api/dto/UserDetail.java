package org.example.risklendpro.api.dto;

/**
 * 用户完整只读明细，供跨服务（贷款审批、风控、管理端）取身份与状态信息。
 */
public record UserDetail(
        Long id,
        String realName,
        String phoneNumber,
        String email,
        String idCard,
        String accountStatus,
        String assessmentStatus,
        Long createTime
) {
}