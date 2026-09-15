package org.example.risklendpro.api.dto;

/**
 * 管理员摘要，供跨服务关联审批人/操作人用户名。
 */
public record AdminProfile(
        Long id,
        String username
) {
}