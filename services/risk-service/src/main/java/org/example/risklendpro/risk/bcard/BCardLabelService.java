package org.example.risklendpro.risk.bcard;

import org.example.risklendpro.risk.entity.BCardFeatureSnapshot;

import java.time.LocalDate;

/**
 * B 卡成熟标签生成（阶段 A2）。
 *
 * <p>标签只能由表现期结束之后的数据生成：{@code label_30dpd_6m} 需要
 * {@code asOfDate + 6 个月} 已过，{@code label_90dpd_12m} 需要 {@code asOfDate + 12 个月} 已过。</p>
 */
public interface BCardLabelService {

    /**
     * 幂等回填：只处理已成熟样本，未成熟的样本保持标签为空且状态不变。
     *
     * @return 本次真正写入过标签的样本数
     */
    int backfillMaturedLabels(int batchSize);

    /**
     * 对单个快照按成熟规则回填标签。
     *
     * @return 发生写入时返回更新后的快照；未成熟或已完成后跳过时返回 null
     */
    BCardFeatureSnapshot applyLabelsIfMatured(BCardFeatureSnapshot snapshot, LocalDate referenceDate);
}