package org.example.risklendpro.risk.admin;

import org.example.risklendpro.risk.admin.AntiFraudHandleRequest;

import java.util.Map;

public interface AdminRiskDataService {

    Map<String, Object> getOverview();

    Map<String, Object> listCreditScores(Integer page, Integer size, String userName, String riskLevel);

    Map<String, Object> getUserRiskDetail(Long userId);

    Map<String, Object> listAntiFraud(Integer page, Integer size, String status);

    Map<String, Object> handleAntiFraud(String id, AntiFraudHandleRequest request, Long adminId);

    Map<String, Object> listMultiLoan(Integer page, Integer size, Integer minActiveLoans);

    Map<String, Object> listCreditReports(Integer page, Integer size, String status);

    Map<String, Object> getCreditReportDetail(Long userId);

    Map<String, Object> exportRiskData(String status);
}
