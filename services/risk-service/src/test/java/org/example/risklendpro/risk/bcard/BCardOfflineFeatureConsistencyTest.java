package org.example.risklendpro.risk.bcard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.risk.mapper.BCardFeatureSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 阶段 A3 必需测试：特征一致性。
 *
 * <p>离线侧 {@code risk-assessment/build_asof_features.py} 与在线侧
 * {@link BCardFeatureServiceImpl} 读取同一份原始还款数据
 * （{@code risk-assessment/data/fixture/asof_consistency_fixture.json}），
 * 必须产出完全一致的 as-of 特征。离线的基准输出由构建脚本生成在
 * {@code risk-assessment/output/b_card_offline_features_golden.csv}。</p>
 *
 * <p>只比较双方都具备的 28 个在线特征；离线独有的 3/6 个月窗口特征
 * （available_online=false）不参与比较，也不允许进入最终模型。</p>
 */
@ExtendWith(MockitoExtension.class)
class BCardOfflineFeatureConsistencyTest {

    private static final String FIXTURE = "risk-assessment/data/fixture/asof_consistency_fixture.json";
    private static final String GOLDEN = "risk-assessment/output/b_card_offline_features_golden.csv";

    private static final List<String> ONLINE_FEATURES = Arrays.asList(
            "loanCountTotal", "activeLoanCount", "repaidLoanCount", "overdueLoanCount",
            "overduePlanCount", "currentMaxDpd", "maxDpd12m", "avgDpd12m", "latePaymentCount12m",
            "onTimeRate12m", "partialPaymentRatio12m", "utilizationRate", "cappedUtilizationRate",
            "overdueAmount", "outstandingAmount", "totalLimit", "usedLimit", "remainingLimit",
            "recentNewLoanCount90d", "recentNewLoanCount180d", "daysSinceLastOverdue", "tenureDays",
            "totalLoanAmount", "avgLoanAmount", "lastLoanAmount", "recordCount", "paidRecordCount",
            "hasOverdue");

    @InjectMocks
    private BCardFeatureServiceImpl service;

    @Mock
    private LoanMapper loanMapper;

    @Mock
    private RepaymentPlanMapper repaymentPlanMapper;

    @Mock
    private RepaymentRecordMapper repaymentRecordMapper;

    @Mock
    private UserCreditLimitMapper userCreditLimitMapper;

    @Mock
    private BCardFeatureSnapshotMapper bCardFeatureSnapshotMapper;

    @Mock
    private BCardCreditLimitHistoryReader creditLimitHistoryReader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void onlineFeatureVectorMatchesOfflineBuilderForEveryFixtureCase() throws Exception {
        JsonNode fixture = objectMapper.readTree(readRepoFile(FIXTURE));
        Map<String, Map<String, String>> golden = readGoldenCsv(readRepoFile(GOLDEN));

        int caseCount = 0;
        for (JsonNode testCase : fixture.get("cases")) {
            String caseName = testCase.get("name").asText();
            long userId = testCase.get("user_id").asLong();
            LocalDate asOfDate = LocalDate.parse(testCase.get("as_of_date").asText());
            Map<String, String> goldenRow = golden.get(caseName);
            assertNotNull(goldenRow, "离线基准缺少用例: " + caseName);

            stubMappersFromFixture(testCase, asOfDate);

            BCardFeatureVector vector = service.buildFeatureVector(userId, asOfDate);
            Map<String, Object> online = toOnlineFeatureMap(vector);

            for (String feature : ONLINE_FEATURES) {
                String expected = goldenValue(goldenRow, feature);
                Object actual = online.get(feature);
                if (expected.isEmpty()) {
                    assertNull(actual, "用例 " + caseName + " 特征 " + feature + " 期望为空");
                } else if (actual instanceof Boolean) {
                    assertEquals(Boolean.parseBoolean(expected), actual,
                            "用例 " + caseName + " 特征 " + feature);
                } else {
                    assertEquals(Double.parseDouble(expected), ((Number) actual).doubleValue(), 1e-9,
                            "用例 " + caseName + " 特征 " + feature);
                }
            }

            assertEquals(goldenValue(goldenRow, "data_quality_flags"),
                    String.join("|", new TreeSet<>(vector.getDataQualityFlags())),
                    "用例 " + caseName + " 数据质量标记");
            assertEquals(goldenValue(goldenRow, "data_quality_status"),
                    BCardFeatureServiceImpl.resolveDataQualityStatus(vector.getDataQualityFlags()),
                    "用例 " + caseName + " 数据质量状态");
            caseCount++;
        }

        assertEquals(3, caseCount, "必须覆盖 fixture 中的全部用例");
    }

    // ------------------------------------------------------------------

    private void stubMappersFromFixture(JsonNode testCase, LocalDate asOfDate) {
        List<Loan> loans = new ArrayList<>();
        for (JsonNode node : testCase.path("loans")) {
            Loan loan = new Loan();
            loan.setLoanId(node.get("loan_id").asLong());
            loan.setUserId(testCase.get("user_id").asLong());
            loan.setStatus(node.get("status").asText());
            loan.setAmount(BigDecimal.valueOf(node.get("amount").asDouble()));
            loan.setApplyTime(toDate(node.path("apply_time").asText(null)));
            loan.setDisbursementTime(toDate(node.path("disbursement_time").asText(null)));
            loans.add(loan);
        }
        when(loanMapper.selectList(any())).thenReturn(loans);

        List<RepaymentPlan> plans = new ArrayList<>();
        for (JsonNode node : testCase.path("plans")) {
            RepaymentPlan plan = new RepaymentPlan();
            plan.setPlanId(node.get("plan_id").asLong());
            plan.setLoanId(node.get("loan_id").asLong());
            plan.setUserId(testCase.get("user_id").asLong());
            plan.setCreateTime(toDate(node.path("create_time").asText(null)));
            plan.setUpdateTime(toDate(node.path("update_time").asText(null)));
            plans.add(plan);
        }
        when(repaymentPlanMapper.selectList(any())).thenReturn(plans);

        List<RepaymentRecord> records = new ArrayList<>();
        for (JsonNode node : testCase.path("records")) {
            RepaymentRecord record = new RepaymentRecord();
            record.setRecordId(node.get("record_id").asLong());
            record.setLoanId(node.get("loan_id").asLong());
            record.setStatus(node.get("status").asText());
            record.setAmount(BigDecimal.valueOf(node.get("amount").asDouble()));
            record.setActualAmount(BigDecimal.valueOf(node.get("actual_amount").asDouble()));
            record.setDueDate(toDate(node.path("due_date").asText(null)));
            record.setRepaymentDate(toDate(node.path("repayment_date").asText(null)));
            record.setCreateTime(toDate(node.path("create_time").asText(null)));
            records.add(record);
        }
        when(repaymentRecordMapper.selectList(any())).thenReturn(records);

        BCardCreditLimitHistoryReader.CreditLimitAsOf latest = null;
        for (JsonNode node : testCase.path("credit_limit_history")) {
            LocalDate snapshotDate = LocalDate.parse(node.get("snapshot_date").asText());
            if (snapshotDate.isAfter(asOfDate)) {
                continue;
            }
            if (latest == null || snapshotDate.isAfter(latest.snapshotDate())) {
                latest = new BCardCreditLimitHistoryReader.CreditLimitAsOf(
                        snapshotDate,
                        node.get("total_limit").asDouble(),
                        node.get("used_limit").asDouble(),
                        node.get("remaining_limit").asDouble(),
                        node.get("overdue_amount").asDouble(),
                        node.get("has_overdue").asBoolean());
            }
        }
        when(creditLimitHistoryReader.findAsOf(any(), any())).thenReturn(Optional.ofNullable(latest));
    }

    private static Map<String, Object> toOnlineFeatureMap(BCardFeatureVector vector) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("loanCountTotal", vector.getLoanCountTotal());
        map.put("activeLoanCount", vector.getActiveLoanCount());
        map.put("repaidLoanCount", vector.getRepaidLoanCount());
        map.put("overdueLoanCount", vector.getOverdueLoanCount());
        map.put("overduePlanCount", vector.getOverduePlanCount());
        map.put("currentMaxDpd", vector.getCurrentMaxDpd());
        map.put("maxDpd12m", vector.getMaxDpd12m());
        map.put("avgDpd12m", vector.getAvgDpd12m());
        map.put("latePaymentCount12m", vector.getLatePaymentCount12m());
        map.put("onTimeRate12m", vector.getOnTimeRate12m());
        map.put("partialPaymentRatio12m", vector.getPartialPaymentRatio12m());
        map.put("utilizationRate", vector.getUtilizationRate());
        map.put("cappedUtilizationRate", vector.getCappedUtilizationRate());
        map.put("overdueAmount", vector.getOverdueAmount());
        map.put("outstandingAmount", vector.getOutstandingAmount());
        map.put("totalLimit", vector.getTotalLimit());
        map.put("usedLimit", vector.getUsedLimit());
        map.put("remainingLimit", vector.getRemainingLimit());
        map.put("recentNewLoanCount90d", vector.getRecentNewLoanCount90d());
        map.put("recentNewLoanCount180d", vector.getRecentNewLoanCount180d());
        map.put("daysSinceLastOverdue", vector.getDaysSinceLastOverdue());
        map.put("tenureDays", vector.getTenureDays());
        map.put("totalLoanAmount", vector.getTotalLoanAmount());
        map.put("avgLoanAmount", vector.getAvgLoanAmount());
        map.put("lastLoanAmount", vector.getLastLoanAmount());
        map.put("recordCount", vector.getRecordCount());
        map.put("paidRecordCount", vector.getPaidRecordCount());
        map.put("hasOverdue", vector.isHasOverdue());
        return map;
    }

    /** 读取离线基准 CSV：case_name -> 列名 -> 值。 */
    private static Map<String, Map<String, String>> readGoldenCsv(String content) {
        String[] lines = content.split("\r?\n");
        String[] header = lines[0].split(",", -1);
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            String[] cells = lines[i].split(",", -1);
            assertEquals(header.length, cells.length, "离线基准行列数不一致: " + cells[0]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                row.put(header[c], cells[c]);
            }
            rows.put(cells[0], row);
        }
        return rows;
    }

    private static String goldenValue(Map<String, String> row, String column) {
        assertNotNull(row, "离线基准缺少被测行");
        String value = row.get(column);
        assertNotNull(value, "离线基准缺少列: " + column);
        return value;
    }

    private static String readRepoFile(String relative) throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            dir = dir.getParent();
        }
        throw new IOException("找不到仓库文件: " + relative + "，当前目录=" + Paths.get("").toAbsolutePath());
    }

    private static Date toDate(String text) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return null;
        }
        return Date.from(LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}