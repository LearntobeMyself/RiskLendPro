package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.LimitAdjustRequest;
import org.example.risklendpro.pojo.response.LimitAdjustResponse;
import org.example.risklendpro.pojo.response.UserCreditLimitResponse;

public interface UserCreditLimitService {
    /**
     * 获取用户额度信息
     */
    UserCreditLimitResponse getUserCreditLimit(Long userId);

    /**
     * 管理员调整用户额度
     */
    LimitAdjustResponse adjustLimit(LimitAdjustRequest request, Long operatorId);
}
