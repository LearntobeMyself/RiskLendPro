package org.example.risklendpro.api.contract;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目录产品「特定身份」对应的证明材料类型，由 risk-service 与 loan-service 共同约定。
 * 证明材料通过 POST /risk/assessment/supplement/upload 上传，本期只校验是否上传，不鉴别真伪。
 */
public final class IdentityProofMaterials {

    public static final String SALARY = "IDENTITY_SALARY_PROOF";
    public static final String SOCIAL_SECURITY = "IDENTITY_SOCIAL_SECURITY";
    public static final String PROVIDENT_FUND = "IDENTITY_PROVIDENT_FUND";
    public static final String WORK_CERT = "IDENTITY_WORK_CERT";
    public static final String BUSINESS_LICENSE = "IDENTITY_BUSINESS_LICENSE";
    public static final String SELF_EMPLOYED_LICENSE = "IDENTITY_SELF_EMPLOYED_LICENSE";

    /** 特定身份 → 可接受的证明材料类型（任选其一即可）。 */
    private static final Map<String, List<String>> BY_GROUP = Map.of(
            "SALARIED", List.of(SALARY, SOCIAL_SECURITY),
            "PROVIDENT_FUND", List.of(PROVIDENT_FUND),
            "CIVIL_SERVANT", List.of(WORK_CERT),
            "SME_OWNER", List.of(BUSINESS_LICENSE),
            "SELF_EMPLOYED", List.of(SELF_EMPLOYED_LICENSE)
    );

    private static final Set<String> ALL_CODES = Set.of(
            SALARY, SOCIAL_SECURITY, PROVIDENT_FUND, WORK_CERT, BUSINESS_LICENSE, SELF_EMPLOYED_LICENSE);

    private IdentityProofMaterials() {
    }

    public static boolean isIdentityProof(String code) {
        return code != null && ALL_CODES.contains(code);
    }

    /**
     * 产品声明的特定身份集合 → 需要的证明材料类型集合。
     * 同一产品声明的多个身份取并集，用户满足其中任意一种即可（例如经营周转贷在企业主/个体工商户里证明一种）。
     */
    public static Set<String> requiredCodes(List<String> targetGroups) {
        if (targetGroups == null || targetGroups.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new HashSet<>();
        for (String group : targetGroups) {
            List<String> acceptable = BY_GROUP.get(group);
            if (acceptable != null) {
                codes.addAll(acceptable);
            }
        }
        return codes;
    }
}
