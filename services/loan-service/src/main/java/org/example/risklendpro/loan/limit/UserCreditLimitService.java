package org.example.risklendpro.loan.limit;

import org.example.risklendpro.loan.limit.LimitAdjustRequest;
import org.example.risklendpro.loan.limit.LimitAdjustResponse;
import org.example.risklendpro.loan.limit.UserCreditLimitResponse;

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
