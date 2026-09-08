package org.example.risklendpro.api.dto;

public record UserSummary(
        Long id,
        String realName,
        String phoneNumber,
        String email,
        String accountStatus
) {
}
