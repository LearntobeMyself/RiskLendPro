package org.example.risklendpro.risk.fraud;

import org.example.risklendpro.risk.credit.Blacklist;
import org.example.risklendpro.risk.credit.UserExternalFeatures;
import org.example.risklendpro.risk.score.CreditScoreEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudRiskServiceTest {

    private final FraudRiskService service = new FraudRiskService();

    @Test
    void blacklistFullAndBadSignalsBecomesCritical() {
        UserExternalFeatures ext = new UserExternalFeatures();
        ext.setCreditBureauWeek(8);
        ext.setCreditBureauMon(15);
        ext.setDaysLastPhoneChange(20);
        ext.setActiveLoansCount(5);
        ext.setPrevRefusedCount(3);
        ext.setLoanOverdueMax6m(3);
        ext.setCcUtilization(BigDecimal.valueOf(1.2));
        ext.setExtSource2(BigDecimal.valueOf(0.1));
        ext.setExtSource3(BigDecimal.valueOf(0.1));

        FraudRiskService.Result result = service.evaluateApplication(
                "8000-15000", blacklist(CreditScoreEngine.MatchLevel.FULL), ext);

        assertEquals(FraudRiskService.Level.CRITICAL, result.level());
        assertEquals(100, result.score());
        assertTrue(result.signals().stream().anyMatch(s -> "BLACKLIST_FULL".equals(s.code())));
    }

    @Test
    void healthyUserIsLowRisk() {
        UserExternalFeatures ext = new UserExternalFeatures();
        ext.setCreditBureauWeek(0);
        ext.setCreditBureauMon(1);
        ext.setDaysLastPhoneChange(900);
        ext.setActiveLoansCount(0);
        ext.setPrevRefusedCount(0);
        ext.setLoanOverdueMax6m(0);
        ext.setCcUtilization(BigDecimal.valueOf(0.1));
        ext.setExtSource2(BigDecimal.valueOf(0.85));
        ext.setExtSource3(BigDecimal.valueOf(0.8));

        FraudRiskService.Result result = service.evaluateApplication(
                "8000-15000", blacklist(CreditScoreEngine.MatchLevel.NONE), ext);

        assertEquals(FraudRiskService.Level.LOW, result.level());
        assertEquals(0, result.score());
    }

    @Test
    void multiHeadAndPhoneChangeBecomesHighRisk() {
        UserExternalFeatures ext = new UserExternalFeatures();
        ext.setCreditBureauWeek(8);
        ext.setCreditBureauMon(15);
        ext.setDaysLastPhoneChange(20);
        ext.setActiveLoansCount(4);
        ext.setPrevRefusedCount(2);

        FraudRiskService.Result result = service.evaluateApplication(
                "5000-8000", blacklist(CreditScoreEngine.MatchLevel.NONE), ext);

        assertEquals(FraudRiskService.Level.HIGH, result.level());
        assertTrue(result.score() >= FraudRiskService.HIGH_SCORE);
    }

    @Test
    void bcardDeteriorationBecomesHighRisk() {
        Map<String, Double> bcard = Map.of(
                "currentMaxDpd", 35.0,
                "onTimeRate12m", 0.3,
                "daysSinceLastOverdue", 10.0,
                "utilizationRate", 0.95);

        FraudRiskService.Result result = service.evaluateUser(
                blacklist(CreditScoreEngine.MatchLevel.NONE), null, bcard, 0);

        assertEquals(FraudRiskService.Level.HIGH, result.level());
        assertTrue(result.signals().stream().anyMatch(s -> "BCARD_DPD_30_PLUS".equals(s.code())));
        assertTrue(result.signals().stream().anyMatch(s -> "BCARD_ON_TIME_LOW".equals(s.code())));
    }

    @Test
    void incomeOverstatementProducesSignal() {
        UserExternalFeatures ext = new UserExternalFeatures();
        ext.setAmtIncomeTotal(BigDecimal.valueOf(72000));

        FraudRiskService.Result result = service.evaluateApplication(
                "15000以上", blacklist(CreditScoreEngine.MatchLevel.NONE), ext);

        assertTrue(result.signals().stream().anyMatch(s -> "INCOME_OVERSTATED".equals(s.code())));
        assertTrue(result.summary().contains("自填收入高于征信"));
    }

    @Test
    void applicationVelocityAddsSignal() {
        FraudRiskService.Result result = service.evaluateUser(
                blacklist(CreditScoreEngine.MatchLevel.NONE), null, Map.of(), 4);

        assertTrue(result.signals().stream().anyMatch(s -> "APPLY_VELOCITY".equals(s.code())));
    }

    private static CreditScoreEngine.BlacklistMatchResult blacklist(CreditScoreEngine.MatchLevel level) {
        return new CreditScoreEngine.BlacklistMatchResult(
                level, level == CreditScoreEngine.MatchLevel.NONE ? null : new Blacklist());
    }
}