package org.example.risklendpro.loan.catalog;

import org.example.risklendpro.loan.catalog.entity.LoanImage;
import org.example.risklendpro.loan.catalog.entity.LoanInstitution;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.LoanTag;
import org.example.risklendpro.loan.catalog.vo.ImageVO;
import org.example.risklendpro.loan.catalog.vo.TagVO;
import org.example.risklendpro.storage.StorageService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CatalogAssembler {

    private final StorageService storageService;

    public CatalogAssembler(StorageService storageService) {
        this.storageService = storageService;
    }

    public ImageVO image(LoanImage image, boolean includeKey) {
        if (image == null) {
            return null;
        }
        ImageVO vo = new ImageVO();
        vo.setImageId(image.getId());
        vo.setImageType(image.getImageType());
        vo.setSort(image.getSort());
        vo.setUrl(storageService.accessUrl(image.getObjectKey()));
        if (includeKey) {
            vo.setObjectKey(image.getObjectKey());
        }
        return vo;
    }

    public TagVO tag(LoanTag tag) {
        TagVO vo = new TagVO();
        vo.setTagId(tag.getId());
        vo.setTagName(tag.getTagName());
        vo.setCategoryCode(tag.getCategoryCode());
        return vo;
    }

    public Map<String, Object> institutionBrief(LoanInstitution institution, ImageVO logo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("institutionId", institution.getId());
        map.put("institutionName", institution.getInstitutionName());
        map.put("institutionType", institution.getInstitutionType());
        map.put("institutionTypeName", CatalogEnums.INSTITUTION_TYPE.get(institution.getInstitutionType()));
        map.put("logo", logo);
        return map;
    }

    public Map<String, Object> productCard(LoanProduct product, LoanInstitution institution, ImageVO cover,
                                           ImageVO logo, List<TagVO> tags) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("productId", product.getId());
        card.put("productName", product.getProductName());
        card.put("institution", institutionBrief(institution, logo));
        card.put("categoryCode", product.getCategoryCode());
        card.put("categoryName", CatalogEnums.CATEGORY.get(product.getCategoryCode()));
        card.put("coverImage", cover);
        card.put("maxAmount", product.getMaxAmount());
        card.put("minAnnualRate", product.getMinAnnualRate());
        card.put("maxAnnualRate", product.getMaxAnnualRate());
        card.put("maxTerm", product.getMaxTerm());
        card.put("termUnit", product.getTermUnit());
        card.put("mortgageRequired", CatalogEnums.named(product.getMortgageRequired(), CatalogEnums.MORTGAGE));
        card.put("rateScope", CatalogEnums.named(product.getRateScope(), CatalogEnums.RATE_SCOPE_CARD));
        card.put("tags", tags);
        return card;
    }

    public Map<String, Object> productDetail(LoanProduct product, LoanInstitution institution, ImageVO cover,
                                             List<ImageVO> details, ImageVO logo, List<TagVO> tags, boolean admin) {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("productId", product.getId());
        overview.put("productName", product.getProductName());
        overview.put("institution", institutionBrief(institution, logo));
        overview.put("categoryCode", product.getCategoryCode());
        overview.put("categoryName", CatalogEnums.CATEGORY.get(product.getCategoryCode()));
        overview.put("tags", tags);
        overview.put("summary", product.getSummary());
        overview.put("coverImage", cover);
        overview.put("detailImages", details == null ? List.of() : details);

        Map<String, Object> loanInfo = new LinkedHashMap<>();
        loanInfo.put("minAmount", product.getMinAmount());
        loanInfo.put("maxAmount", product.getMaxAmount());
        loanInfo.put("minTerm", product.getMinTerm());
        loanInfo.put("maxTerm", product.getMaxTerm());
        loanInfo.put("termUnit", product.getTermUnit());
        loanInfo.put("minAnnualRate", product.getMinAnnualRate());
        loanInfo.put("maxAnnualRate", product.getMaxAnnualRate());
        loanInfo.put("rateCalcMethod", CatalogEnums.named(product.getRateCalcMethod(), CatalogEnums.RATE_CALC));
        loanInfo.put("rateScope", CatalogEnums.named(product.getRateScope(), CatalogEnums.RATE_SCOPE));
        loanInfo.put("repaymentMethods", namedList(CatalogJson.readStringList(product.getRepaymentMethods()), CatalogEnums.REPAYMENT_METHOD));
        loanInfo.put("disbursementTime", product.getDisbursementTime());

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("targetGroups", namedList(CatalogJson.readStringList(product.getTargetGroups()), CatalogEnums.TARGET_GROUP));
        conditions.put("minAge", product.getMinAge());
        conditions.put("maxAge", product.getMaxAge());
        conditions.put("incomeRequirement", product.getIncomeRequirement());
        conditions.put("creditRequirement", product.getCreditRequirement());
        conditions.put("occupationRequirement", product.getOccupationRequirement());
        conditions.put("mortgageRequired", CatalogEnums.named(product.getMortgageRequired(), CatalogEnums.MORTGAGE));
        conditions.put("regions", CatalogJson.readStringList(product.getRegions()));
        conditions.put("extraConditions", CatalogJson.readMap(product.getExtraConditions()));

        Map<String, Object> others = new LinkedHashMap<>();
        others.put("feeStatus", CatalogEnums.named(product.getFeeStatus(), CatalogEnums.FEE_STATUS));
        others.put("feeDescription", product.getFeeDescription());
        others.put("prepaymentDescription", product.getPrepaymentDescription());
        others.put("remark", product.getRemark());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("dataSource", product.getDataSource());
        meta.put("verifiedAt", product.getVerifiedAt() == null ? null : product.getVerifiedAt().toString());
        meta.put("updateTime", KnowledgeRenderer.dateTime(product.getUpdateTime()));
        meta.put("riskTip", KnowledgeRenderer.RISK_TIP);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("overview", overview);
        detail.put("loanInfo", loanInfo);
        detail.put("conditions", conditions);
        detail.put("materials", CatalogJson.readStringList(product.getMaterials()));
        detail.put("others", others);
        detail.put("meta", meta);
        if (!admin) {
            detail.put("inPlatformApply", ProductApplySupport.inPlatformApply(product));
        }
        if (admin) {
            detail.put("status", product.getStatus());
            detail.put("everOnShelf", product.getEverOnShelf());
            detail.put("version", product.getVersion());
            detail.put("sortWeight", product.getSortWeight());
        }
        return detail;
    }

    public int completeness(LoanProduct product) {
        int filled = 0;
        if (product.getMinAmount() != null) filled++;
        if (product.getMaxAmount() != null) filled++;
        if (product.getMinTerm() != null) filled++;
        if (product.getMaxTerm() != null) filled++;
        if (product.getMinAnnualRate() != null) filled++;
        if (product.getMaxAnnualRate() != null) filled++;
        if (!CatalogJson.readStringList(product.getRepaymentMethods()).isEmpty()) filled++;
        if (product.getDisbursementTime() != null && !product.getDisbursementTime().isBlank()) filled++;
        if (!CatalogJson.readStringList(product.getTargetGroups()).isEmpty()) filled++;
        if (product.getMinAge() != null) filled++;
        if (product.getCreditRequirement() != null && !product.getCreditRequirement().isBlank()) filled++;
        if (!CatalogJson.readStringList(product.getMaterials()).isEmpty()) filled++;
        return filled * 100 / 12;
    }

    private static List<Map<String, String>> namedList(List<String> codes, Map<String, String> dict) {
        List<Map<String, String>> list = new ArrayList<>();
        for (String code : codes) {
            Map<String, String> named = CatalogEnums.named(code, dict);
            if (named != null) {
                list.add(named);
            }
        }
        return list;
    }
}
