package org.example.risklendpro.api.dto;

/**
 * 用户摘要，供跨服务只读展示（发邮件、展示姓名/手机等）。
 */
public record UserSummary(
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
