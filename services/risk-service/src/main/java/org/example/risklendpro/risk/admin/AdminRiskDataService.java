package org.example.risklendpro.risk.admin;

import org.example.risklendpro.risk.admin.AntiFraudHandleRequest;
import org.springframework.http.ResponseEntity;

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

    /** 直接返回 CSV 文件流，不再返回二次下载地址。 */
    ResponseEntity<byte[]> exportRiskData(String status);
}
