package org.example.risklendpro.loan.limit;

import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：修复9（调额防负值 —— 已用额度 / 剩余额度不得为负）。
 */
@ExtendWith(MockitoExtension.class)
class UserCreditLimitServiceImplTest {

    @InjectMocks
    private UserCreditLimitServiceImpl service;

    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;

    @Mock
    private LimitAdjustLogMapper limitAdjustLogMapper;

    private UserCreditLimit creditLimit(BigDecimal total, BigDecimal used, BigDecimal remaining) {
        UserCreditLimit cl = new UserCreditLimit();
        cl.setUserId(7L);
        cl.setTotalLimit(total);
        cl.setUsedLimit(used);
        cl.setRemainingLimit(remaining);
        return cl;
    }

    private LimitAdjustRequest request(BigDecimal newLimit) {
        LimitAdjustRequest req = new LimitAdjustRequest();
        req.setUserId(7L);
        req.setNewLimit(newLimit);
        req.setReason("test");
        return req;
    }

    @Test
    void adjustLimit_growingUsedLimit_throwsOnNegativeUsed() {
        // 原额度 1000，已用 200，剩余 800；大幅调低到 100
        when(userCreditLimitMapper.selectOne(any())).thenReturn(
                creditLimit(BigDecimal.valueOf(1000), BigDecimal.valueOf(200), BigDecimal.valueOf(800)));

        assertThrows(RuntimeException.class, () -> service.adjustLimit(request(BigDecimal.valueOf(100)), 0L));
        verify(userCreditLimitMapper, never()).updateById(any());
        verify(limitAdjustLogMapper, never()).insert(any());
    }

    @Test
    void adjustLimit_growingRemainingOverdraw_throwsOnNegativeRemaining() {
        // 原额度 1000，已用 200，剩余 800；调低到 500 会导致剩余 = 800 - 500 = 300 >= 0 合法（不抛）
        // 改为验证明显的透支：原额度 1000 已用 900 剩余 100，调到 0 → 剩余 = -900
        when(userCreditLimitMapper.selectOne(any())).thenReturn(
                creditLimit(BigDecimal.valueOf(1000), BigDecimal.valueOf(900), BigDecimal.valueOf(100)));

        assertThrows(RuntimeException.class, () -> service.adjustLimit(request(BigDecimal.ZERO), 0L));
        verify(userCreditLimitMapper, never()).updateById(any());
        verify(limitAdjustLogMapper, never()).insert(any());
    }

    @Test
    void adjustLimit_validChangePersists() {
        when(userCreditLimitMapper.selectOne(any())).thenReturn(
                creditLimit(BigDecimal.valueOf(1000), BigDecimal.valueOf(200), BigDecimal.valueOf(800)));

        service.adjustLimit(request(BigDecimal.valueOf(1200)), 0L);

        verify(userCreditLimitMapper).updateById(any());
        verify(limitAdjustLogMapper).insert(any());
    }
}