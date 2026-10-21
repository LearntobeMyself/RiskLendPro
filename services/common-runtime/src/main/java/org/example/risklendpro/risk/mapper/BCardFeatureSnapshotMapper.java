package org.example.risklendpro.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.risklendpro.risk.entity.BCardFeatureSnapshot;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BCardFeatureSnapshotMapper extends BaseMapper<BCardFeatureSnapshot> {

    /**
     * 待回填标签的样本：尚未完成全部标签，且 6 个月窗口已经结束
     * （{@code as_of_date <= cutoff} 即 {@code asOfDate + 6 个月 <= 参考日}）。
     *
     * <p>只按 as_of_date 粗筛，具体每个标签是否成熟由服务端按
     * maturity_date_30dpd_6m / maturity_date_90dpd_12m 再判断，
     * 保证未成熟样本不会被提前打标签。</p>
     */
    @Select("SELECT * FROM b_card_feature_snapshot "
            + "WHERE sample_status IN ('OPEN', 'MATURED') "
            + "AND feature_version = #{featureVersion} "
            + "AND as_of_date <= #{cutoff} "
            + "ORDER BY as_of_date ASC, id ASC "
            + "LIMIT #{limit}")
    List<BCardFeatureSnapshot> selectLabelCandidates(@Param("featureVersion") String featureVersion,
                                                     @Param("cutoff") LocalDate cutoff,
                                                     @Param("limit") int limit);

    /**
     * 已可按指定特征/标签版本导出的训练样本（两个标签齐备）。
     * 旧口径（如 BF_V2.0 / BF_V2.1）的快照永远不会被计入。
     */
    @Select("SELECT COUNT(*) FROM b_card_feature_snapshot "
            + "WHERE sample_status = 'LABELED' "
            + "AND label_version = #{labelVersion} "
            + "AND feature_version = #{featureVersion}")
    long countLabeledSamples(@Param("featureVersion") String featureVersion,
                             @Param("labelVersion") String labelVersion);
}