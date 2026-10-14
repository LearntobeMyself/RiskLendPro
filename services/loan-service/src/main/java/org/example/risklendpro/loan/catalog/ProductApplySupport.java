package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanInstitution;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.mapper.LoanInstitutionMapper;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductApplySupport {

    public static final String CHANNEL_DIRECT = "DIRECT";
    public static final String CHANNEL_PRODUCT = "PRODUCT";
    public static final BigDecimal DIRECT_RATE = new BigDecimal("0.05");

    private static final Map<String, String> METHOD_TO_CHINESE = Map.of(
            "EQUAL_INSTALLMENT", "等额本息",
            "EQUAL_PRINCIPAL", "等额本金",
            "INTEREST_FIRST", "先息后本",
            "等额本息", "等额本息",
            "等额本金", "等额本金",
            "先息后本", "先息后本"
    );
    private static final Map<String, String> CHINESE_TO_CODE = Map.of(
            "等额本息", "EQUAL_INSTALLMENT",
            "等额本金", "EQUAL_PRINCIPAL",
            "先息后本", "INTEREST_FIRST"
    );
    private static final Set<String> CALCULATOR_CODES = Set.of(
            "EQUAL_INSTALLMENT", "EQUAL_PRINCIPAL", "INTEREST_FIRST");

    private final LoanProductMapper productMapper;
    private final LoanInstitutionMapper institutionMapper;

    public ProductApplySupport(LoanProductMapper productMapper, LoanInstitutionMapper institutionMapper) {
        this.productMapper = productMapper;
        this.institutionMapper = institutionMapper;
    }

    public static boolean inPlatformApply(LoanProduct product) {
        if (product == null || !"MONTH".equals(product.getTermUnit())) {
            return false;
        }
        if (product.getMinAmount() == null && product.getMaxAmount() == null) {
            return false;
        }
        if (product.getMinAnnualRate() == null && product.getMaxAnnualRate() == null) {
            return false;
        }
        List<String> methods = CatalogJson.readStringList(product.getRepaymentMethods());
        if (methods.isEmpty()) {
            return true;
        }
        return methods.stream().anyMatch(CALCULATOR_CODES::contains);
    }

    public ResolvedApply resolve(Long productId, BigDecimal amount, Integer termMonths, String repaymentMethod) {
        LoanProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("产品不存在或已下架");
        }
        if (!"ON_SHELF".equals(product.getStatus())) {
            throw new RuntimeException("产品不存在或已下架");
        }
        LoanInstitution institution = institutionMapper.selectById(product.getInstitutionId());
        if (institution == null || institution.getEnabled() == null || institution.getEnabled() != 1) {
            throw new RuntimeException("产品不存在或已下架");
        }
        if (!inPlatformApply(product)) {
            throw new RuntimeException("该产品暂不支持在本平台申请，请选择按月且已录入额度和利率的产品，或使用直接借款");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("借款金额必须大于0");
        }
        if (product.getMinAmount() != null && amount.compareTo(BigDecimal.valueOf(product.getMinAmount())) < 0) {
            throw new RuntimeException("借款金额低于该产品下限");
        }
        if (product.getMaxAmount() != null && amount.compareTo(BigDecimal.valueOf(product.getMaxAmount())) > 0) {
            throw new RuntimeException("借款金额超过该产品上限");
        }
        if (termMonths == null || termMonths < 1) {
            throw new RuntimeException("还款期限必须大于0");
        }
        if (product.getMinTerm() != null && termMonths < product.getMinTerm()) {
            throw new RuntimeException("还款期限低于该产品下限");
        }
        if (product.getMaxTerm() != null && termMonths > product.getMaxTerm()) {
            throw new RuntimeException("还款期限超过该产品上限");
        }
        String chineseMethod = normalizeRepaymentMethod(repaymentMethod);
        List<String> allowed = CatalogJson.readStringList(product.getRepaymentMethods());
        if (!allowed.isEmpty()) {
            String code = CHINESE_TO_CODE.get(chineseMethod);
            if (!allowed.contains(code)) {
                throw new RuntimeException("还款方式不在该产品支持范围内");
            }
        }
        Map<String, Object> extra = CatalogJson.readMap(product.getExtraConditions());
        return new ResolvedApply(
                product.getId(),
                product.getProductName(),
                CHANNEL_PRODUCT,
                toLoanRate(product),
                chineseMethod,
                product.getCategoryCode(),
                product.getMortgageRequired(),
                strList(extra.get("collateralTypes")),
                decimal(extra.get("maxLoanToValue")),
                product.getMinAge(),
                product.getMaxAge(),
                CatalogJson.readStringList(product.getTargetGroups()),
                CatalogJson.readStringList(product.getMaterials())
        );
    }

    public static String normalizeRepaymentMethod(String repaymentMethod) {
        if (repaymentMethod == null || repaymentMethod.isBlank()) {
            throw new RuntimeException("还款方式不能为空");
        }
        String chinese = METHOD_TO_CHINESE.get(repaymentMethod.trim());
        if (chinese == null) {
            throw new RuntimeException("不支持的还款方式: " + repaymentMethod);
        }
        return chinese;
    }

    static BigDecimal toLoanRate(LoanProduct product) {
        BigDecimal percent = product.getMinAnnualRate() != null
                ? product.getMinAnnualRate()
                : product.getMaxAnnualRate();
        return percent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    }

    /** 抵押贷款分类 */
    public static final String CATEGORY_MORTGAGE = "MORTGAGE";
    public static final String COLLATERAL_HOUSE = "HOUSE";
    public static final String COLLATERAL_VEHICLE = "VEHICLE";

    public static boolean isMortgageCategory(String categoryCode) {
        return CATEGORY_MORTGAGE.equals(categoryCode);
    }

    /** 从产品的可接受抵押物类型里取一个用于标记借据的抵押物类型。 */
    public static String collateralTypeOf(List<String> collateralTypes) {
        if (collateralTypes == null) {
            return null;
        }
        if (collateralTypes.contains(COLLATERAL_HOUSE)) {
            return COLLATERAL_HOUSE;
        }
        if (collateralTypes.contains(COLLATERAL_VEHICLE)) {
            return COLLATERAL_VEHICLE;
        }
        return null;
    }

    private static List<String> strList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(v -> v != null).map(String::valueOf).toList();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record ResolvedApply(
            Long productId,
            String productName,
            String applyChannel,
            BigDecimal interestRate,
            String repaymentMethod,
            String categoryCode,
            String mortgageRequired,
            List<String> collateralTypes,
            BigDecimal maxLoanToValue,
            Integer minAge,
            Integer maxAge,
            List<String> targetGroups,
            List<String> materials
    ) {
        /** 直接借款（无产品）时使用，仅带利率与还款方式。 */
        public ResolvedApply(Long productId, String productName, String applyChannel,
                             BigDecimal interestRate, String repaymentMethod) {
            this(productId, productName, applyChannel, interestRate, repaymentMethod,
                    null, null, List.of(), null, null, null, List.of(), List.of());
        }
    }
}
