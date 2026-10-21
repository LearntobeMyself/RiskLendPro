package org.example.risklendpro.risk.bcard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.risk.entity.BCardFeatureSnapshot;
import org.example.risklendpro.risk.mapper.BCardFeatureSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 成熟标签实现（阶段 A2）。
 *
 * <p>标签定义（事件型，天然覆盖结清 / 未结清 / 展期 / 核销各种业务状态）：</p>
 * <ul>
 *   <li>{@code label_30dpd_6m}：表现期 {@code [asOfDate+1, asOfDate+6个月]} 内是否出现过 DPD &gt;= 30 的事件；</li>
 *   <li>{@code label_90dpd_12m}：表现期 {@code [asOfDate+1, asOfDate+12个月]} 内是否出现过 DPD &gt;= 90 的事件；</li>
 *   <li>未还清的期次 DPD 计算到表现期结束日（核销、长期未结清因此自然落入事件）；</li>
 *   <li>表现期结束时仍未出现 DPD 事件的样本才是 0；「未逾期」不等于「好样本」，
 *       必须等表现期真正结束才判定。</li>
 * </ul>
 *
 * <p>样本状态流转：OPEN →（6 个月窗口结束）MATURED →（12 个月窗口结束、两个标签齐备）LABELED；
 * 数据质量不合格的样本在快照创建阶段即为 EXCLUDED，不参与训练。</p>
 */
@Service
public class BCardLabelServiceImpl implements BCardLabelService {

    private static final Logger log = LoggerFactory.getLogger(BCardLabelServiceImpl.class);

    /** 标签口径版本，训练导出必须与之一致。 */
    public static final String LABEL_VERSION = "LBL_V1";

    static final int LABEL_30DPD_6M_THRESHOLD = 30;
    static final int LABEL_90DPD_12M_THRESHOLD = 90;
    static final int LABEL_30DPD_6M_MONTHS = 6;
    static final int LABEL_90DPD_12M_MONTHS = 12;

    @Autowired
    private BCardFeatureSnapshotMapper bCardFeatureSnapshotMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    /** 离线回放与测试可指定参考日；生产为空，取系统当天。 */
    private LocalDate referenceDateOverride;

    @Override
    public int backfillMaturedLabels(int batchSize) {
        LocalDate referenceDate = referenceDate();
        // 最小 horizon 是 6 个月，早于该上界的样本不可能成熟，先在 SQL 侧过滤
        LocalDate cutoff = referenceDate.minusMonths(LABEL_30DPD_6M_MONTHS);
        // 只处理当前特征口径的快照：旧口径（BF_V2.0/BF_V2.1）存在时间穿越，
        // 既不打标签也不计入训练样本
        List<BCardFeatureSnapshot> candidates = bCardFeatureSnapshotMapper.selectLabelCandidates(
                BCardFeatureServiceImpl.FEATURE_VERSION, cutoff, batchSize);

        int labeled = 0;
        for (BCardFeatureSnapshot candidate : candidates) {
            try {
                if (applyLabelsIfMatured(candidate, referenceDate) != null) {
                    labeled++;
                }
            } catch (Exception e) {
                log.warn("B 卡标签回填失败 snapshotId={} userId={}",
                        candidate.getId(), candidate.getUserId(), e);
            }
        }
        log.info("B 卡标签回填扫描完成 referenceDate={} 候选={} 写入={}",
                referenceDate, candidates.size(), labeled);
        return labeled;
    }

    @Override
    @Transactional
    public BCardFeatureSnapshot applyLabelsIfMatured(BCardFeatureSnapshot snapshot, LocalDate referenceDate) {
        if (snapshot == null || snapshot.getAsOfDate() == null) {
            return null;
        }
        if (BCardFeatureServiceImpl.STATUS_EXCLUDED.equals(snapshot.getSampleStatus())) {
            return null;
        }
        // 幂等：已完成且标签版本一致的样本不重复覆盖
        if (BCardFeatureServiceImpl.STATUS_LABELED.equals(snapshot.getSampleStatus())
                && LABEL_VERSION.equals(snapshot.getLabelVersion())
                && snapshot.getLabel30dpd6m() != null
                && snapshot.getLabel90dpd12m() != null) {
            return null;
        }

        LocalDate maturity6m = snapshot.getMaturityDate30dpd6m() != null
                ? snapshot.getMaturityDate30dpd6m()
                : snapshot.getAsOfDate().plusMonths(LABEL_30DPD_6M_MONTHS);
        LocalDate maturity12m = snapshot.getMaturityDate90dpd12m() != null
                ? snapshot.getMaturityDate90dpd12m()
                : snapshot.getAsOfDate().plusMonths(LABEL_90DPD_12M_MONTHS);

        // 未成熟时绝不生成标签
        if (maturity6m.isAfter(referenceDate)) {
            return null;
        }

        List<RepaymentRecord> records = loadUserRecords(snapshot.getUserId());
        LocalDate performanceStart = snapshot.getAsOfDate().plusDays(1);

        Integer label30 = computeLabel(records, performanceStart, maturity6m, LABEL_30DPD_6M_THRESHOLD);
        // 注意：不要写成三元表达式，否则 Integer 与 int 混合会拆箱 null 抛 NPE
        Integer label90 = snapshot.getLabel90dpd12m();
        if (!maturity12m.isAfter(referenceDate)) {
            label90 = computeLabel(records, performanceStart, maturity12m, LABEL_90DPD_12M_THRESHOLD);
        }

        snapshot.setMaturityDate30dpd6m(maturity6m);
        snapshot.setMaturityDate90dpd12m(maturity12m);
        snapshot.setLabel30dpd6m(label30);
        snapshot.setLabel90dpd12m(label90);
        snapshot.setLabelVersion(LABEL_VERSION);
        snapshot.setLabelCalculatedAt(LocalDateTime.now());
        // 两个标签齐备才算 LABELED（可训练）；只成熟了 6 个月标签的样本停在 MATURED
        snapshot.setSampleStatus(label30 != null && label90 != null
                ? BCardFeatureServiceImpl.STATUS_LABELED
                : BCardFeatureServiceImpl.STATUS_MATURED);
        bCardFeatureSnapshotMapper.updateById(snapshot);

        log.info("B 卡标签已生成 snapshotId={} userId={} asOfDate={} label30={} label90={} status={}",
                snapshot.getId(), snapshot.getUserId(), snapshot.getAsOfDate(),
                label30, label90, snapshot.getSampleStatus());
        return snapshot;
    }

    /**
     * 标签值 = 表现期内最大 DPD 是否达到阈值。
     *
     * <p>未还清期次的 DPD 计算到表现期结束日；已经还清的期次按实际还款日计算；
     * 若逾期在表现期开始前就已结清，则不计入本期表现。</p>
     */
    static int computeLabel(List<RepaymentRecord> records,
                            LocalDate windowStart,
                            LocalDate windowEnd,
                            int threshold) {
        return maxDpdWithinWindow(records, windowStart, windowEnd) >= threshold ? 1 : 0;
    }

    static int maxDpdWithinWindow(List<RepaymentRecord> records, LocalDate windowStart, LocalDate windowEnd) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        int maxDpd = 0;
        for (RepaymentRecord record : records) {
            LocalDate dueDate = toLocalDate(record.getDueDate());
            if (dueDate == null || dueDate.isAfter(windowEnd)) {
                continue;
            }
            LocalDate repaymentDate = toLocalDate(record.getRepaymentDate());
            LocalDate effectiveEnd = repaymentDate != null && !repaymentDate.isAfter(windowEnd)
                    ? repaymentDate
                    : windowEnd;
            if (effectiveEnd.isBefore(windowStart)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(dueDate, effectiveEnd);
            if (days > 0) {
                maxDpd = Math.max(maxDpd, (int) days);
            }
        }
        return maxDpd;
    }

    private List<RepaymentRecord> loadUserRecords(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<Long> loanIds = loanMapper.selectList(new QueryWrapper<Loan>().eq("user_id", userId))
                .stream()
                .map(Loan::getLoanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (loanIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 标签允许使用表现期内的全部数据，故不做 as-of 过滤
        return repaymentRecordMapper.selectList(
                new QueryWrapper<RepaymentRecord>().in("loan_id", loanIds));
    }

    private LocalDate referenceDate() {
        return referenceDateOverride != null ? referenceDateOverride : LocalDate.now();
    }

    /** 供离线回放/测试指定参考日；传入 null 恢复为系统当天。 */
    public void setReferenceDateForReplay(LocalDate referenceDate) {
        this.referenceDateOverride = referenceDate;
    }

    private static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}