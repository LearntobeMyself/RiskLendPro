package org.example.risklendpro.loan.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.dto.ImagePresignRequest;
import org.example.risklendpro.loan.catalog.dto.InstitutionSaveRequest;
import org.example.risklendpro.loan.catalog.dto.ProductSaveRequest;
import org.example.risklendpro.loan.catalog.dto.ShelfRequest;
import org.example.risklendpro.loan.catalog.dto.TagSaveRequest;
import org.example.risklendpro.loan.catalog.entity.LoanCategory;
import org.example.risklendpro.loan.catalog.entity.LoanImage;
import org.example.risklendpro.loan.catalog.entity.LoanInstitution;
import org.example.risklendpro.loan.catalog.entity.LoanOperationLog;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.catalog.entity.LoanProductTag;
import org.example.risklendpro.loan.catalog.entity.LoanTag;
import org.example.risklendpro.loan.catalog.vo.ImageVO;
import org.example.risklendpro.loan.catalog.vo.TagVO;
import org.example.risklendpro.loan.mapper.LoanCategoryMapper;
import org.example.risklendpro.loan.mapper.LoanImageMapper;
import org.example.risklendpro.loan.mapper.LoanInstitutionMapper;
import org.example.risklendpro.loan.mapper.LoanOperationLogMapper;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.example.risklendpro.loan.mapper.LoanProductTagMapper;
import org.example.risklendpro.loan.mapper.LoanTagMapper;
import org.example.risklendpro.storage.PresignResult;
import org.example.risklendpro.storage.StorageProperties;
import org.example.risklendpro.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LoanCatalogService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private final LoanCategoryMapper categoryMapper;
    private final LoanTagMapper tagMapper;
    private final LoanInstitutionMapper institutionMapper;
    private final LoanProductMapper productMapper;
    private final LoanProductTagMapper productTagMapper;
    private final LoanImageMapper imageMapper;
    private final LoanOperationLogMapper operationLogMapper;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final CatalogAssembler assembler;
    private final CatalogCache catalogCache;

    public LoanCatalogService(
            LoanCategoryMapper categoryMapper,
            LoanTagMapper tagMapper,
            LoanInstitutionMapper institutionMapper,
            LoanProductMapper productMapper,
            LoanProductTagMapper productTagMapper,
            LoanImageMapper imageMapper,
            LoanOperationLogMapper operationLogMapper,
            StorageService storageService,
            StorageProperties storageProperties,
            CatalogAssembler assembler,
            CatalogCache catalogCache) {
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.institutionMapper = institutionMapper;
        this.productMapper = productMapper;
        this.productTagMapper = productTagMapper;
        this.imageMapper = imageMapper;
        this.operationLogMapper = operationLogMapper;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.assembler = assembler;
        this.catalogCache = catalogCache;
    }

    public Map<String, Object> filters() {
        Map<String, Object> cached = catalogCache.getFilters(Map.class);
        if (cached != null) {
            return cached;
        }
        List<LoanCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<LoanCategory>().orderByAsc(LoanCategory::getSort));
        List<LoanTag> tags = tagMapper.selectList(new LambdaQueryWrapper<LoanTag>().orderByAsc(LoanTag::getSort));
        Map<String, List<TagVO>> tagsByCategory = new HashMap<>();
        List<TagVO> commonTags = new ArrayList<>();
        for (LoanTag tag : tags) {
            TagVO vo = assembler.tag(tag);
            if (tag.getCategoryCode() == null) {
                commonTags.add(vo);
            } else {
                tagsByCategory.computeIfAbsent(tag.getCategoryCode(), k -> new ArrayList<>()).add(vo);
            }
        }
        List<Map<String, Object>> categoryVos = new ArrayList<>();
        for (LoanCategory category : categories) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("categoryCode", category.getCode());
            item.put("categoryName", category.getName());
            item.put("description", category.getDescription());
            item.put("icon", categoryIcon(category.getCode(), false));
            item.put("productCount", countOnShelf(category.getCode()));
            item.put("tags", tagsByCategory.getOrDefault(category.getCode(), List.of()));
            categoryVos.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categoryVos);
        result.put("commonTags", commonTags);
        result.put("institutionTypes", CatalogEnums.options(CatalogEnums.INSTITUTION_TYPE));
        result.put("targetGroups", CatalogEnums.options(CatalogEnums.TARGET_GROUP));
        result.put("repaymentMethods", CatalogEnums.options(CatalogEnums.REPAYMENT_METHOD));
        result.put("mortgageOptions", CatalogEnums.options(CatalogEnums.MORTGAGE));
        result.put("sortOptions", CatalogEnums.options(CatalogEnums.SORT_BY));
        catalogCache.putFilters(result);
        return result;
    }

    public Map<String, Object> userList(String keyword, String categoryCode, List<String> institutionTypes,
                                        Long amount, BigDecimal maxRate, Integer term, String mortgageRequired,
                                        String targetGroup, List<Long> tagIds, String sortBy,
                                        Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 50) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "分页参数不合法");
        }
        if (keyword != null && keyword.length() > 30) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "关键词最长30字");
        }
        QueryWrapper<LoanProduct> wrapper = visibleWrapper();
        applyFilters(wrapper, keyword, categoryCode, institutionTypes, amount, maxRate, term, mortgageRequired, targetGroup, tagIds);
        applySort(wrapper, sortBy);
        Page<LoanProduct> page = productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = page.getRecords().stream().map(p -> toCard(p, false)).toList();
        return pageResult(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> userDetail(Long productId) {
        Map cached = catalogCache.getProduct(productId, Map.class);
        if (cached != null) {
            return cached;
        }
        LoanProduct product = requireProduct(productId);
        if (!isVisible(product)) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_OFF_SHELF, "产品已下架");
        }
        Map<String, Object> detail = toDetail(product, false);
        catalogCache.putProduct(productId, detail);
        return detail;
    }

    public Map<String, Object> userInstitution(Long institutionId) {
        LoanInstitution institution = requireEnabledInstitution(institutionId);
        List<LoanProduct> products = productMapper.selectList(visibleWrapper()
                .eq("institution_id", institutionId)
                .last("limit 50"));
        Map<String, Object> result = new LinkedHashMap<>(assembler.institutionBrief(institution, logo(institution, false)));
        result.put("introduction", institution.getIntroduction());
        result.put("products", products.stream().map(p -> toCard(p, false)).toList());
        return result;
    }

    public List<Map<String, Object>> similar(Long productId, Integer size) {
        int limit = size == null ? 4 : Math.min(Math.max(size, 1), 10);
        LoanProduct current = requireProduct(productId);
        if (!isVisible(current)) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_OFF_SHELF, "产品已下架");
        }
        Set<Long> currentTags = tagIdsOf(productId);
        List<LoanProduct> candidates = productMapper.selectList(visibleWrapper()
                .eq("category_code", current.getCategoryCode())
                .ne("id", productId));
        return candidates.stream()
                .sorted((a, b) -> {
                    int cmp = Long.compare(overlap(tagIdsOf(b.getId()), currentTags), overlap(tagIdsOf(a.getId()), currentTags));
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Integer.compare(nz(b.getSortWeight()), nz(a.getSortWeight()));
                })
                .limit(limit)
                .map(p -> toCard(p, false))
                .toList();
    }

    public Map<String, Object> compare(List<Long> productIds) {
        if (productIds == null || productIds.size() < 2 || productIds.size() > 4) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "对比产品须为2到4个");
        }
        boolean hasOff = false;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Long id : productIds) {
            LoanProduct product = productMapper.selectById(id);
            if (product == null || !isVisible(product)) {
                list.add(null);
                hasOff = true;
            } else {
                list.add(toDetail(product, false));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("hint", hasOff ? "部分产品不在展示范围" : null);
        return result;
    }

    @Transactional
    public Map<String, Object> presign(ImagePresignRequest request, Long operatorId) {
        CatalogEnums.requireKnown(request.getImageType(), CatalogEnums.IMAGE_TYPE, "imageType");
        if (request.getFileName() == null || !request.getFileName().contains(".")) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "文件名不合法");
        }
        if (!IMAGE_TYPES.contains(request.getContentType())) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "仅支持 jpeg/png/webp");
        }
        if (request.getFileSize() == null || request.getFileSize() <= 0 || request.getFileSize() > MAX_IMAGE_SIZE) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "图片大小须在5MB以内");
        }
        String ext = request.getFileName().substring(request.getFileName().lastIndexOf('.') + 1).toLowerCase();
        String month = DateTimeFormatter.ofPattern("yyyyMM").format(LocalDateTime.now());
        String objectKey = "loan/" + request.getImageType().toLowerCase() + "/" + month + "/" + UUID.randomUUID() + "." + ext;
        LoanImage image = new LoanImage();
        image.setObjectKey(objectKey);
        image.setImageType(request.getImageType());
        image.setStatus("TEMP");
        image.setContentType(request.getContentType());
        image.setFileSize(request.getFileSize());
        image.setSort(0);
        image.setCreateBy(operatorId);
        image.setCreateTime(LocalDateTime.now());
        imageMapper.insert(image);
        Duration expire = Duration.ofMinutes(Math.max(storageProperties.getPresignExpireMinutes(), 1));
        PresignResult presign = storageService.presignPut(objectKey, request.getContentType(), expire);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imageId", image.getId());
        result.put("objectKey", objectKey);
        result.put("uploadUrl", presign.uploadUrl());
        result.put("method", presign.method());
        result.put("headers", presign.headers());
        result.put("expireAt", KnowledgeRenderer.dateTime(presign.expireAt()));
        return result;
    }

    public ImageVO confirmUpload(Long imageId, boolean includeKey) {
        LoanImage image = imageMapper.selectById(imageId);
        if (image == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.IMAGE_INVALID, "图片不存在");
        }
        if (!storageService.exists(image.getObjectKey())) {
            throw new CatalogBusinessException(CatalogErrorCodes.IMAGE_NOT_UPLOADED, "对象存储中找不到该文件");
        }
        return assembler.image(image, includeKey);
    }

    public Map<String, Object> adminDetail(Long productId) {
        LoanProduct product = requireProduct(productId);
        Map<String, Object> detail = toDetail(product, true);
        detail.put("tagIds", tagIdsOf(productId).stream().toList());
        return detail;
    }

    public Map<String, Object> adminList(String keyword, String categoryCode, Long institutionId, String status,
                                         Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 50) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "分页参数不合法");
        }
        QueryWrapper<LoanProduct> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("product_name", keyword.trim());
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            wrapper.eq("category_code", categoryCode);
        }
        if (institutionId != null) {
            wrapper.eq("institution_id", institutionId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("update_time");
        Page<LoanProduct> page = productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (LoanProduct product : page.getRecords()) {
            Map<String, Object> card = toCard(product, true);
            card.put("status", product.getStatus());
            card.put("updateBy", product.getUpdateBy());
            card.put("updateTime", KnowledgeRenderer.dateTime(product.getUpdateTime()));
            card.put("verifiedAt", product.getVerifiedAt());
            card.put("completeness", assembler.completeness(product));
            list.add(card);
        }
        return pageResult(list, page.getTotal(), pageNum, pageSize);
    }

    @Transactional
    public Map<String, Object> createProduct(ProductSaveRequest request, Long operatorId) {
        LoanProduct product = new LoanProduct();
        product.setStatus("DRAFT");
        product.setEverOnShelf(0);
        product.setCreateBy(operatorId);
        applyProduct(product, request, true);
        product.setUpdateBy(operatorId);
        productMapper.insert(product);
        replaceTags(product.getId(), request.getTagIds(), product.getCategoryCode());
        bindProductImages(product.getId(), request.getCoverImageId(), request.getDetailImageIds());
        writeLog("PRODUCT", product.getId(), "CREATE", Map.of("productName", product.getProductName()), operatorId);
        catalogCache.evictAll();
        return Map.of("productId", product.getId());
    }

    @Transactional
    public Map<String, Object> updateProduct(Long productId, ProductSaveRequest request, Long operatorId) {
        LoanProduct product = requireProduct(productId);
        if (request.getVersion() == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "version必填");
        }
        product.setVersion(request.getVersion());
        applyProduct(product, request, false);
        product.setUpdateBy(operatorId);
        touchKnowledge(product);
        int rows = productMapper.updateById(product);
        if (rows == 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.VERSION_CONFLICT, "数据已被他人修改，请刷新");
        }
        replaceTags(productId, request.getTagIds(), product.getCategoryCode());
        bindProductImages(productId, request.getCoverImageId(), request.getDetailImageIds());
        writeLog("PRODUCT", productId, "UPDATE", Map.of("productName", product.getProductName()), operatorId);
        catalogCache.evictAll();
        LoanProduct latest = requireProduct(productId);
        return Map.of("version", latest.getVersion());
    }

    @Transactional
    public Map<String, Object> changeShelf(ShelfRequest request, Long operatorId) {
        if (request.getProductIds() == null || request.getProductIds().isEmpty() || request.getProductIds().size() > 50) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "productIds须为1到50个");
        }
        if (!"ON_SHELF".equals(request.getTargetStatus()) && !"OFF_SHELF".equals(request.getTargetStatus())) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "targetStatus只能是ON_SHELF或OFF_SHELF");
        }
        List<Long> successIds = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long productId : request.getProductIds()) {
            try {
                LoanProduct product = requireProduct(productId);
                if ("ON_SHELF".equals(request.getTargetStatus())) {
                    List<String> missing = onShelfMissing(product);
                    if (!missing.isEmpty()) {
                        failed.add(Map.of("productId", productId, "reason", "ON_SHELF_CHECK_FAILED", "missingFields", missing));
                        continue;
                    }
                    product.setStatus("ON_SHELF");
                    product.setEverOnShelf(1);
                    product.setKnowledgeTime(LocalDateTime.now());
                    productMapper.updateById(product);
                    writeLog("PRODUCT", productId, "ON_SHELF", Map.of(), operatorId);
                } else {
                    if ("ON_SHELF".equals(product.getStatus())) {
                        product.setStatus("OFF_SHELF");
                        product.setLastOffShelfTime(LocalDateTime.now());
                        touchKnowledge(product);
                        productMapper.updateById(product);
                        writeLog("PRODUCT", productId, "OFF_SHELF", Map.of(), operatorId);
                    }
                }
                successIds.add(productId);
            } catch (CatalogBusinessException e) {
                failed.add(Map.of("productId", productId, "reason", e.getMessage()));
            }
        }
        catalogCache.evictAll();
        return Map.of("successIds", successIds, "failed", failed);
    }

    @Transactional
    public void deleteProduct(Long productId, Long operatorId) {
        LoanProduct product = requireProduct(productId);
        if (product.getEverOnShelf() != null && product.getEverOnShelf() == 1) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_CANNOT_DELETE, "已上架过的产品不能删除");
        }
        unbindImages("COVER", String.valueOf(productId));
        unbindImages("DETAIL", String.valueOf(productId));
        productTagMapper.delete(new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getProductId, productId));
        productMapper.deleteById(productId);
        writeLog("PRODUCT", productId, "DELETE", Map.of(), operatorId);
        catalogCache.evictAll();
    }

    public List<Map<String, Object>> extraTemplates(String categoryCode) {
        CatalogEnums.requireKnown(categoryCode, CatalogEnums.CATEGORY, "categoryCode");
        return switch (categoryCode) {
            case "BUSINESS" -> List.of(
                    field("minEstablishYears", "企业成立年限下限", "INTEGER", "年", null),
                    field("minOperatingMonths", "经营时间下限", "INTEGER", "个月", null),
                    field("revenueRequirement", "营业收入要求", "STRING", null, null),
                    field("licenseRequirement", "营业执照要求", "STRING", null, null)
            );
            case "MORTGAGE" -> List.of(
                    field("collateralTypes", "可接受抵押物类型", "MULTI_SELECT", null, CatalogEnums.options(CatalogEnums.COLLATERAL)),
                    field("collateralValueRequirement", "抵押物价值要求", "STRING", null, null),
                    field("maxLoanToValue", "抵押率上限", "DECIMAL", "%", null)
            );
            case "CONSUMER_INSTALLMENT" -> List.of(
                    field("scenes", "适用场景", "MULTI_SELECT", null, CatalogEnums.options(CatalogEnums.SCENE)),
                    field("merchantScope", "合作商户范围", "STRING", null, null),
                    field("paidToMerchant", "是否直接支付给商户", "BOOLEAN", null, null)
            );
            default -> List.of();
        };
    }

    public List<Map<String, Object>> exportKnowledge(List<Long> productIds, LocalDateTime updatedSince, String format) {
        QueryWrapper<LoanProduct> wrapper = new QueryWrapper<LoanProduct>().eq("ever_on_shelf", 1);
        if (productIds != null && !productIds.isEmpty()) {
            wrapper.in("id", productIds);
        }
        if (updatedSince != null) {
            wrapper.gt("knowledge_time", updatedSince);
        }
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (LoanProduct product : productMapper.selectList(wrapper)) {
            LoanInstitution institution = institutionMapper.selectById(product.getInstitutionId());
            String institutionName = institution == null ? "" : institution.getInstitutionName();
            String typeName = institution == null ? "" : CatalogEnums.INSTITUTION_TYPE.get(institution.getInstitutionType());
            List<String> tagNames = tagsOf(product.getId()).stream().map(TagVO::getTagName).toList();
            String content = KnowledgeRenderer.render(product, institutionName, typeName, tagNames);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("chunkType", "PRODUCT");
            metadata.put("categoryCode", product.getCategoryCode());
            metadata.put("institutionName", institutionName);
            metadata.put("tags", tagNames);
            metadata.put("status", product.getStatus());
            metadata.put("verifiedAt", product.getVerifiedAt() == null ? null : product.getVerifiedAt().toString());
            metadata.put("updateTime", KnowledgeRenderer.dateTime(product.getKnowledgeTime()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", product.getId());
            item.put("chunkId", "product_" + product.getId());
            item.put("title", product.getProductName() + "（" + institutionName + "）");
            item.put("content", content);
            item.put("metadata", metadata);
            chunks.add(item);
        }
        return chunks;
    }

    public Map<String, Object> institutionList(String keyword, String institutionType, Integer enabled,
                                               Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 50) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "分页参数不合法");
        }
        LambdaQueryWrapper<LoanInstitution> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(LoanInstitution::getInstitutionName, keyword.trim());
        }
        if (institutionType != null && !institutionType.isBlank()) {
            wrapper.eq(LoanInstitution::getInstitutionType, institutionType);
        }
        if (enabled != null) {
            wrapper.eq(LoanInstitution::getEnabled, enabled);
        }
        wrapper.orderByDesc(LoanInstitution::getUpdateTime);
        Page<LoanInstitution> page = institutionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (LoanInstitution institution : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>(assembler.institutionBrief(institution, logo(institution, true)));
            item.put("introduction", institution.getIntroduction());
            item.put("enabled", institution.getEnabled());
            item.put("version", institution.getVersion());
            item.put("productCount", productMapper.selectCount(new LambdaQueryWrapper<LoanProduct>()
                    .eq(LoanProduct::getInstitutionId, institution.getId())));
            item.put("onShelfCount", productMapper.selectCount(new LambdaQueryWrapper<LoanProduct>()
                    .eq(LoanProduct::getInstitutionId, institution.getId())
                    .eq(LoanProduct::getStatus, "ON_SHELF")));
            list.add(item);
        }
        return pageResult(list, page.getTotal(), pageNum, pageSize);
    }

    @Transactional
    public Map<String, Object> createInstitution(InstitutionSaveRequest request, Long operatorId) {
        LoanInstitution institution = new LoanInstitution();
        applyInstitution(institution, request, true);
        institution.setEnabled(1);
        institution.setCreateBy(operatorId);
        institution.setUpdateBy(operatorId);
        institutionMapper.insert(institution);
        bindLogo(institution.getId(), request.getLogoImageId());
        writeLog("INSTITUTION", institution.getId(), "CREATE", Map.of("name", institution.getInstitutionName()), operatorId);
        catalogCache.evictAll();
        return Map.of("institutionId", institution.getId());
    }

    @Transactional
    public Map<String, Object> updateInstitution(Long institutionId, InstitutionSaveRequest request, Long operatorId) {
        LoanInstitution institution = requireInstitution(institutionId);
        if (request.getVersion() == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "version必填");
        }
        boolean identityChanged = !institution.getInstitutionName().equals(CatalogValidator.requireName(request.getInstitutionName(), 64, "institutionName"))
                || !institution.getInstitutionType().equals(request.getInstitutionType());
        institution.setVersion(request.getVersion());
        applyInstitution(institution, request, false);
        institution.setUpdateBy(operatorId);
        if (institutionMapper.updateById(institution) == 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.VERSION_CONFLICT, "数据已被他人修改，请刷新");
        }
        bindLogo(institutionId, request.getLogoImageId());
        if (identityChanged) {
            touchInstitutionProducts(institutionId);
        }
        writeLog("INSTITUTION", institutionId, "UPDATE", Map.of(), operatorId);
        catalogCache.evictAll();
        return Map.of("version", requireInstitution(institutionId).getVersion());
    }

    @Transactional
    public Map<String, Object> enableInstitution(Long institutionId, boolean enabled, Long operatorId) {
        LoanInstitution institution = requireInstitution(institutionId);
        institution.setEnabled(enabled ? 1 : 0);
        institution.setUpdateBy(operatorId);
        institutionMapper.updateById(institution);
        List<Long> autoOff = new ArrayList<>();
        if (!enabled) {
            List<LoanProduct> onShelf = productMapper.selectList(new LambdaQueryWrapper<LoanProduct>()
                    .eq(LoanProduct::getInstitutionId, institutionId)
                    .eq(LoanProduct::getStatus, "ON_SHELF"));
            for (LoanProduct product : onShelf) {
                product.setStatus("OFF_SHELF");
                product.setLastOffShelfTime(LocalDateTime.now());
                touchKnowledge(product);
                productMapper.updateById(product);
                writeLog("PRODUCT", product.getId(), "AUTO_OFF_SHELF", Map.of("institutionId", institutionId), operatorId);
                autoOff.add(product.getId());
            }
            writeLog("INSTITUTION", institutionId, "DISABLE", Map.of("autoOffShelfProductIds", autoOff), operatorId);
        } else {
            writeLog("INSTITUTION", institutionId, "ENABLE", Map.of(), operatorId);
        }
        catalogCache.evictAll();
        return Map.of("autoOffShelfProductIds", autoOff);
    }

    public List<TagVO> tagList(String categoryCode) {
        LambdaQueryWrapper<LoanTag> wrapper = new LambdaQueryWrapper<LoanTag>().orderByAsc(LoanTag::getSort);
        if ("COMMON".equals(categoryCode)) {
            wrapper.isNull(LoanTag::getCategoryCode);
        } else if (categoryCode != null && !categoryCode.isBlank()) {
            wrapper.eq(LoanTag::getCategoryCode, categoryCode);
        }
        List<TagVO> list = new ArrayList<>();
        for (LoanTag tag : tagMapper.selectList(wrapper)) {
            TagVO vo = assembler.tag(tag);
            vo.setProductCount(Math.toIntExact(productTagMapper.selectCount(
                    new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getTagId, tag.getId()))));
            list.add(vo);
        }
        return list;
    }

    @Transactional
    public Map<String, Object> saveTag(TagSaveRequest request, Long operatorId) {
        String name = CatalogValidator.requireName(request.getTagName(), 20, "tagName");
        String categoryCode = CatalogValidator.blankToNull(request.getCategoryCode());
        if (categoryCode != null) {
            CatalogEnums.requireKnown(categoryCode, CatalogEnums.CATEGORY, "categoryCode");
        }
        ensureTagNameUnique(name, categoryCode, request.getTagId());
        if (request.getTagId() == null) {
            LoanTag tag = new LoanTag();
            tag.setTagName(name);
            tag.setCategoryCode(categoryCode);
            tag.setSort(request.getSort() == null ? 0 : request.getSort());
            tagMapper.insert(tag);
            writeLog("TAG", tag.getId(), "CREATE", Map.of("tagName", name), operatorId);
            catalogCache.evictAll();
            return Map.of("tagId", tag.getId());
        }
        LoanTag existing = tagMapper.selectById(request.getTagId());
        if (existing == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.TAG_NOT_FOUND, "标签不存在");
        }
        boolean categoryChanged = (existing.getCategoryCode() == null && categoryCode != null)
                || (existing.getCategoryCode() != null && !existing.getCategoryCode().equals(categoryCode));
        if (categoryChanged) {
            long used = productTagMapper.selectCount(new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getTagId, existing.getId()));
            if (used > 0) {
                throw new CatalogBusinessException(CatalogErrorCodes.TAG_IN_USE, "标签已被产品使用", used);
            }
        }
        boolean renamed = !name.equals(existing.getTagName());
        existing.setTagName(name);
        existing.setCategoryCode(categoryCode);
        if (request.getSort() != null) {
            existing.setSort(request.getSort());
        }
        tagMapper.updateById(existing);
        if (renamed) {
            List<LoanProductTag> refs = productTagMapper.selectList(
                    new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getTagId, existing.getId()));
            for (LoanProductTag ref : refs) {
                LoanProduct product = productMapper.selectById(ref.getProductId());
                if (product != null) {
                    touchKnowledge(product);
                    productMapper.updateById(product);
                }
            }
        }
        writeLog("TAG", existing.getId(), "UPDATE", Map.of("tagName", name), operatorId);
        catalogCache.evictAll();
        return Map.of("tagId", existing.getId());
    }

    @Transactional
    public void deleteTag(Long tagId) {
        LoanTag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.TAG_NOT_FOUND, "标签不存在");
        }
        long used = productTagMapper.selectCount(new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getTagId, tagId));
        if (used > 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.TAG_IN_USE, "标签已被产品使用", used);
        }
        tagMapper.deleteById(tagId);
        catalogCache.evictAll();
    }

    public int cleanupTempImages() {
        LocalDateTime expire = LocalDateTime.now().minusHours(24);
        List<LoanImage> temps = imageMapper.selectList(new LambdaQueryWrapper<LoanImage>()
                .eq(LoanImage::getStatus, "TEMP")
                .lt(LoanImage::getCreateTime, expire));
        int removed = 0;
        for (LoanImage image : temps) {
            try {
                storageService.delete(image.getObjectKey());
            } catch (CatalogBusinessException ignored) {
                // 未配置 COS 时仍删除登记，避免脏数据堆积
            }
            imageMapper.deleteById(image.getId());
            removed++;
        }
        return removed;
    }

    private void applyProduct(LoanProduct product, ProductSaveRequest request, boolean creating) {
        String name = CatalogValidator.requireName(request.getProductName(), 64, "productName");
        LoanInstitution institution = requireInstitution(request.getInstitutionId());
        CatalogEnums.requireKnown(request.getCategoryCode(), CatalogEnums.CATEGORY, "categoryCode");
        ensureProductNameUnique(name, request.getInstitutionId(), creating ? null : product.getId());
        ProductSaveRequest.LoanInfo loan = CatalogValidator.loanInfo(request.getLoanInfo());
        ProductSaveRequest.Conditions conditions = CatalogValidator.conditions(request.getConditions());
        ProductSaveRequest.Others others = CatalogValidator.others(request.getOthers());
        CatalogValidator.amountRange(loan.getMinAmount(), loan.getMaxAmount());
        String termUnit = CatalogValidator.termUnit(loan.getMinTerm(), loan.getMaxTerm(), loan.getTermUnit());
        CatalogValidator.rateRange(loan.getMinAnnualRate(), loan.getMaxAnnualRate());
        CatalogEnums.requireKnown(loan.getRateCalcMethod(), CatalogEnums.RATE_CALC, "rateCalcMethod");
        CatalogEnums.requireKnown(loan.getRateScope(), CatalogEnums.RATE_SCOPE, "rateScope");
        CatalogValidator.fee(others.getFeeStatus(), others.getFeeDescription());
        CatalogValidator.ageRange(conditions.getMinAge(), conditions.getMaxAge());
        String mortgage = CatalogValidator.applyMortgageRule(request.getCategoryCode(), conditions.getMortgageRequired());
        List<String> repayments = CatalogValidator.enumList(loan.getRepaymentMethods(), CatalogEnums.REPAYMENT_METHOD, "repaymentMethods");
        List<String> groups = CatalogValidator.targetGroups(conditions.getTargetGroups());
        List<String> regions = CatalogValidator.regions(conditions.getRegions());
        List<String> materials = CatalogJson.unique(request.getMaterials());
        Map<String, Object> extra = CatalogValidator.extraConditions(request.getCategoryCode(), conditions.getExtraConditions());
        CatalogValidator.validateExtraValues(request.getCategoryCode(), extra);
        CatalogValidator.length(request.getSummary(), 500, "summary");
        CatalogValidator.length(loan.getDisbursementTime(), 100, "disbursementTime");
        CatalogValidator.length(conditions.getIncomeRequirement(), 255, "incomeRequirement");
        CatalogValidator.length(conditions.getCreditRequirement(), 255, "creditRequirement");
        CatalogValidator.length(conditions.getOccupationRequirement(), 255, "occupationRequirement");
        CatalogValidator.length(others.getFeeDescription(), 1000, "feeDescription");
        CatalogValidator.length(others.getPrepaymentDescription(), 500, "prepaymentDescription");
        CatalogValidator.length(others.getRemark(), 500, "remark");
        CatalogValidator.length(request.getDataSource(), 255, "dataSource");
        product.setProductName(name);
        product.setInstitutionId(institution.getId());
        product.setCategoryCode(request.getCategoryCode());
        product.setSummary(CatalogValidator.blankToNull(request.getSummary()));
        product.setMinAmount(loan.getMinAmount());
        product.setMaxAmount(loan.getMaxAmount());
        product.setMinTerm(loan.getMinTerm());
        product.setMaxTerm(loan.getMaxTerm());
        product.setTermUnit(termUnit);
        product.setMinAnnualRate(loan.getMinAnnualRate());
        product.setMaxAnnualRate(loan.getMaxAnnualRate());
        product.setRateCalcMethod(CatalogValidator.blankToNull(loan.getRateCalcMethod()));
        product.setRateScope(CatalogValidator.blankToNull(loan.getRateScope()));
        product.setRepaymentMethods(CatalogJson.writeList(repayments));
        product.setDisbursementTime(CatalogValidator.blankToNull(loan.getDisbursementTime()));
        product.setTargetGroups(CatalogJson.writeList(groups));
        product.setMinAge(conditions.getMinAge());
        product.setMaxAge(conditions.getMaxAge());
        product.setIncomeRequirement(CatalogValidator.blankToNull(conditions.getIncomeRequirement()));
        product.setCreditRequirement(CatalogValidator.blankToNull(conditions.getCreditRequirement()));
        product.setOccupationRequirement(CatalogValidator.blankToNull(conditions.getOccupationRequirement()));
        product.setMortgageRequired(mortgage);
        product.setRegions(CatalogJson.writeList(regions));
        product.setExtraConditions(CatalogJson.writeMap(extra));
        product.setMaterials(CatalogJson.writeList(materials));
        product.setFeeStatus(CatalogValidator.blankToNull(others.getFeeStatus()));
        product.setFeeDescription("NONE".equals(others.getFeeStatus()) ? null : CatalogValidator.blankToNull(others.getFeeDescription()));
        product.setPrepaymentDescription(CatalogValidator.blankToNull(others.getPrepaymentDescription()));
        product.setRemark(CatalogValidator.blankToNull(others.getRemark()));
        product.setDataSource(CatalogValidator.blankToNull(request.getDataSource()));
        product.setVerifiedAt(request.getVerifiedAt());
        product.setSortWeight(request.getSortWeight() == null ? 0 : request.getSortWeight());
    }

    private void applyInstitution(LoanInstitution institution, InstitutionSaveRequest request, boolean creating) {
        String name = CatalogValidator.requireName(request.getInstitutionName(), 64, "institutionName");
        CatalogEnums.requireKnown(request.getInstitutionType(), CatalogEnums.INSTITUTION_TYPE, "institutionType");
        CatalogValidator.length(request.getIntroduction(), 1000, "introduction");
        LambdaQueryWrapper<LoanInstitution> unique = new LambdaQueryWrapper<LoanInstitution>()
                .eq(LoanInstitution::getInstitutionName, name);
        if (!creating) {
            unique.ne(LoanInstitution::getId, institution.getId());
        }
        if (institutionMapper.selectCount(unique) > 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.INSTITUTION_NAME_DUPLICATE, "机构名重复");
        }
        institution.setInstitutionName(name);
        institution.setInstitutionType(request.getInstitutionType());
        institution.setIntroduction(CatalogValidator.blankToNull(request.getIntroduction()));
    }

    private void replaceTags(Long productId, List<Long> tagIds, String categoryCode) {
        List<Long> ids = CatalogJson.uniqueLong(tagIds);
        if (ids.size() > 10) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "标签最多10个");
        }
        productTagMapper.delete(new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getProductId, productId));
        for (Long tagId : ids) {
            LoanTag tag = tagMapper.selectById(tagId);
            if (tag == null) {
                throw new CatalogBusinessException(CatalogErrorCodes.TAG_NOT_FOUND, "标签不存在");
            }
            if (tag.getCategoryCode() != null && !tag.getCategoryCode().equals(categoryCode)) {
                throw new CatalogBusinessException(CatalogErrorCodes.TAG_CATEGORY_MISMATCH, "标签与产品分类不匹配");
            }
            LoanProductTag rel = new LoanProductTag();
            rel.setProductId(productId);
            rel.setTagId(tagId);
            productTagMapper.insert(rel);
        }
    }

    private void bindProductImages(Long productId, Long coverImageId, List<Long> detailImageIds) {
        Set<Long> keep = new HashSet<>();
        if (coverImageId != null) {
            bindImage(coverImageId, "COVER", String.valueOf(productId), 0);
            keep.add(coverImageId);
        }
        List<Long> details = CatalogJson.uniqueLong(detailImageIds);
        if (details.size() > 9) {
            throw new CatalogBusinessException(CatalogErrorCodes.IMAGE_INVALID, "详情图最多9张");
        }
        int sort = 1;
        for (Long imageId : details) {
            bindImage(imageId, "DETAIL", String.valueOf(productId), sort++);
            keep.add(imageId);
        }
        List<LoanImage> existing = imageMapper.selectList(new LambdaQueryWrapper<LoanImage>()
                .eq(LoanImage::getBizId, String.valueOf(productId))
                .in(LoanImage::getImageType, List.of("COVER", "DETAIL")));
        for (LoanImage image : existing) {
            if (!keep.contains(image.getId())) {
                image.setStatus("TEMP");
                image.setBizId(null);
                imageMapper.updateById(image);
            }
        }
    }

    private void bindLogo(Long institutionId, Long logoImageId) {
        if (logoImageId != null) {
            bindImage(logoImageId, "INSTITUTION_LOGO", String.valueOf(institutionId), 0);
        }
        List<LoanImage> existing = imageMapper.selectList(new LambdaQueryWrapper<LoanImage>()
                .eq(LoanImage::getBizId, String.valueOf(institutionId))
                .eq(LoanImage::getImageType, "INSTITUTION_LOGO"));
        for (LoanImage image : existing) {
            if (logoImageId == null || !image.getId().equals(logoImageId)) {
                image.setStatus("TEMP");
                image.setBizId(null);
                imageMapper.updateById(image);
            }
        }
        LoanInstitution institution = requireInstitution(institutionId);
        if (logoImageId == null) {
            institution.setLogoKey(null);
        } else {
            institution.setLogoKey(imageMapper.selectById(logoImageId).getObjectKey());
        }
        institutionMapper.updateById(institution);
    }

    private void bindImage(Long imageId, String expectedType, String bizId, int sort) {
        LoanImage image = imageMapper.selectById(imageId);
        if (image == null || !expectedType.equals(image.getImageType())) {
            throw new CatalogBusinessException(CatalogErrorCodes.IMAGE_INVALID, "图片不存在或类型不符");
        }
        if ("BOUND".equals(image.getStatus()) && image.getBizId() != null && !bizId.equals(image.getBizId())) {
            throw new CatalogBusinessException(CatalogErrorCodes.IMAGE_INVALID, "图片已被其他对象使用");
        }
        image.setStatus("BOUND");
        image.setBizId(bizId);
        image.setSort(sort);
        imageMapper.updateById(image);
    }

    private void unbindImages(String imageType, String bizId) {
        List<LoanImage> images = imageMapper.selectList(new LambdaQueryWrapper<LoanImage>()
                .eq(LoanImage::getImageType, imageType)
                .eq(LoanImage::getBizId, bizId));
        for (LoanImage image : images) {
            image.setStatus("TEMP");
            image.setBizId(null);
            imageMapper.updateById(image);
        }
    }

    private List<String> onShelfMissing(LoanProduct product) {
        List<String> missing = new ArrayList<>();
        if (blank(product.getProductName())) missing.add("productName");
        if (product.getInstitutionId() == null) missing.add("institutionId");
        if (blank(product.getCategoryCode())) missing.add("categoryCode");
        if (cover(product.getId()) == null) missing.add("coverImage");
        if (blank(product.getSummary())) missing.add("summary");
        if (blank(product.getDataSource())) missing.add("dataSource");
        if (product.getVerifiedAt() == null) missing.add("verifiedAt");
        LoanInstitution institution = institutionMapper.selectById(product.getInstitutionId());
        if (institution == null || institution.getEnabled() == null || institution.getEnabled() != 1) {
            missing.add("institutionEnabled");
        }
        return missing;
    }

    private QueryWrapper<LoanProduct> visibleWrapper() {
        return new QueryWrapper<LoanProduct>()
                .eq("status", "ON_SHELF")
                .inSql("institution_id", "select id from loan_institution where enabled = 1 and deleted = 0");
    }

    private void applyFilters(QueryWrapper<LoanProduct> wrapper, String keyword, String categoryCode,
                              List<String> institutionTypes, Long amount, BigDecimal maxRate, Integer term,
                              String mortgageRequired, String targetGroup, List<Long> tagIds) {
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(w -> w.like("product_name", like)
                    .or()
                    .inSql("institution_id", "select id from loan_institution where deleted = 0 and institution_name like concat('%','" + like.replace("'", "") + "','%')"));
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            wrapper.eq("category_code", categoryCode);
        }
        if (institutionTypes != null && !institutionTypes.isEmpty()) {
            String in = institutionTypes.stream()
                    .filter(CatalogEnums.INSTITUTION_TYPE::containsKey)
                    .map(v -> "'" + v + "'")
                    .collect(Collectors.joining(","));
            if (!in.isEmpty()) {
                wrapper.inSql("institution_id", "select id from loan_institution where deleted = 0 and institution_type in (" + in + ")");
            }
        }
        if (amount != null) {
            wrapper.apply("min_amount is not null and max_amount is not null and min_amount <= {0} and max_amount >= {0}", amount);
        }
        if (maxRate != null) {
            wrapper.apply("min_annual_rate is not null and min_annual_rate <= {0}", maxRate);
        }
        if (term != null) {
            wrapper.apply("term_unit = 'MONTH' and min_term is not null and max_term is not null and min_term <= {0} and max_term >= {0}", term);
        }
        if (mortgageRequired != null && !mortgageRequired.isBlank()) {
            wrapper.eq("mortgage_required", mortgageRequired);
        }
        if (targetGroup != null && !targetGroup.isBlank()) {
            wrapper.apply("JSON_CONTAINS(target_groups, {0})", "\"" + targetGroup.replace("\"", "") + "\"");
        }
        List<Long> tags = CatalogJson.uniqueLong(tagIds);
        if (!tags.isEmpty()) {
            String in = tags.stream().map(String::valueOf).collect(Collectors.joining(","));
            wrapper.inSql("id", "select product_id from loan_product_tag where tag_id in (" + in
                    + ") group by product_id having count(distinct tag_id) = " + tags.size());
        }
    }

    private void applySort(QueryWrapper<LoanProduct> wrapper, String sortBy) {
        String code = sortBy == null || sortBy.isBlank() ? "DEFAULT" : sortBy;
        CatalogEnums.requireKnown(code, CatalogEnums.SORT_BY, "sortBy");
        switch (code) {
            case "RATE_ASC" -> wrapper.last("order by min_annual_rate is null, min_annual_rate asc, sort_weight desc");
            case "AMOUNT_DESC" -> wrapper.last("order by max_amount is null, max_amount desc, sort_weight desc");
            case "TERM_DESC" -> wrapper.last("order by max_term is null, max_term desc, sort_weight desc");
            case "NEWEST" -> wrapper.orderByDesc("create_time");
            default -> wrapper.orderByDesc("sort_weight").orderByDesc("update_time");
        }
    }

    private Map<String, Object> toCard(LoanProduct product, boolean admin) {
        LoanInstitution institution = requireInstitution(product.getInstitutionId());
        return assembler.productCard(product, institution, coverVo(product.getId(), admin), logo(institution, admin), tagsOf(product.getId()));
    }

    private Map<String, Object> toDetail(LoanProduct product, boolean admin) {
        LoanInstitution institution = requireInstitution(product.getInstitutionId());
        return assembler.productDetail(product, institution, coverVo(product.getId(), admin),
                detailVos(product.getId(), admin), logo(institution, admin), tagsOf(product.getId()), admin);
    }

    private ImageVO coverVo(Long productId, boolean admin) {
        return assembler.image(cover(productId), admin);
    }

    private LoanImage cover(Long productId) {
        return imageMapper.selectOne(new LambdaQueryWrapper<LoanImage>()
                .eq(LoanImage::getImageType, "COVER")
                .eq(LoanImage::getBizId, String.valueOf(productId))
                .eq(LoanImage::getStatus, "BOUND")
                .last("limit 1"));
    }

    private List<ImageVO> detailVos(Long productId, boolean admin) {
        return imageMapper.selectList(new LambdaQueryWrapper<LoanImage>()
                        .eq(LoanImage::getImageType, "DETAIL")
                        .eq(LoanImage::getBizId, String.valueOf(productId))
                        .eq(LoanImage::getStatus, "BOUND")
                        .orderByAsc(LoanImage::getSort))
                .stream()
                .map(img -> assembler.image(img, admin))
                .toList();
    }

    private ImageVO logo(LoanInstitution institution, boolean admin) {
        if (institution.getLogoKey() == null) {
            return null;
        }
        LoanImage image = imageMapper.selectOne(new LambdaQueryWrapper<LoanImage>()
                .eq(LoanImage::getObjectKey, institution.getLogoKey())
                .last("limit 1"));
        return assembler.image(image, admin);
    }

    private ImageVO categoryIcon(String categoryCode, boolean admin) {
        LoanImage image = imageMapper.selectOne(new LambdaQueryWrapper<LoanImage>()
                .eq(LoanImage::getImageType, "CATEGORY_ICON")
                .eq(LoanImage::getBizId, categoryCode)
                .eq(LoanImage::getStatus, "BOUND")
                .last("limit 1"));
        return assembler.image(image, admin);
    }

    private List<TagVO> tagsOf(Long productId) {
        List<Long> ids = tagIdsOf(productId).stream().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectBatchIds(ids).stream().map(assembler::tag).toList();
    }

    private Set<Long> tagIdsOf(Long productId) {
        return productTagMapper.selectList(new LambdaQueryWrapper<LoanProductTag>().eq(LoanProductTag::getProductId, productId))
                .stream()
                .map(LoanProductTag::getTagId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private long overlap(Set<Long> a, Set<Long> b) {
        Set<Long> copy = new HashSet<>(a);
        copy.retainAll(b);
        return copy.size();
    }

    private long countOnShelf(String categoryCode) {
        return productMapper.selectCount(visibleWrapper().eq("category_code", categoryCode));
    }

    private boolean isVisible(LoanProduct product) {
        if (!"ON_SHELF".equals(product.getStatus())) {
            return false;
        }
        LoanInstitution institution = institutionMapper.selectById(product.getInstitutionId());
        return institution != null && institution.getEnabled() != null && institution.getEnabled() == 1;
    }

    private LoanProduct requireProduct(Long productId) {
        LoanProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_NOT_FOUND, "产品不存在");
        }
        return product;
    }

    private LoanInstitution requireInstitution(Long institutionId) {
        LoanInstitution institution = institutionMapper.selectById(institutionId);
        if (institution == null) {
            throw new CatalogBusinessException(CatalogErrorCodes.INSTITUTION_NOT_FOUND, "机构不存在");
        }
        return institution;
    }

    private LoanInstitution requireEnabledInstitution(Long institutionId) {
        LoanInstitution institution = requireInstitution(institutionId);
        if (institution.getEnabled() == null || institution.getEnabled() != 1) {
            throw new CatalogBusinessException(CatalogErrorCodes.INSTITUTION_NOT_FOUND, "机构不存在或已停用");
        }
        return institution;
    }

    private void ensureProductNameUnique(String name, Long institutionId, Long excludeId) {
        LambdaQueryWrapper<LoanProduct> wrapper = new LambdaQueryWrapper<LoanProduct>()
                .eq(LoanProduct::getProductName, name)
                .eq(LoanProduct::getInstitutionId, institutionId);
        if (excludeId != null) {
            wrapper.ne(LoanProduct::getId, excludeId);
        }
        if (productMapper.selectCount(wrapper) > 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.PRODUCT_NAME_DUPLICATE, "同机构下产品名重复");
        }
    }

    private void ensureTagNameUnique(String name, String categoryCode, Long excludeId) {
        LambdaQueryWrapper<LoanTag> wrapper = new LambdaQueryWrapper<LoanTag>().eq(LoanTag::getTagName, name);
        if (categoryCode == null) {
            wrapper.isNull(LoanTag::getCategoryCode);
        } else {
            wrapper.and(w -> w.eq(LoanTag::getCategoryCode, categoryCode).or().isNull(LoanTag::getCategoryCode));
        }
        if (excludeId != null) {
            wrapper.ne(LoanTag::getId, excludeId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "同分类下标签名已存在");
        }
    }

    private void touchKnowledge(LoanProduct product) {
        if (product.getEverOnShelf() != null && product.getEverOnShelf() == 1) {
            product.setKnowledgeTime(LocalDateTime.now());
        }
    }

    private void touchInstitutionProducts(Long institutionId) {
        List<LoanProduct> products = productMapper.selectList(new LambdaQueryWrapper<LoanProduct>()
                .eq(LoanProduct::getInstitutionId, institutionId)
                .eq(LoanProduct::getEverOnShelf, 1));
        for (LoanProduct product : products) {
            product.setKnowledgeTime(LocalDateTime.now());
            productMapper.updateById(product);
        }
    }

    private void writeLog(String bizType, Long bizId, String action, Object detail, Long operatorId) {
        LoanOperationLog log = new LoanOperationLog();
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setAction(action);
        log.setDetail(CatalogJson.writeMap(detail instanceof Map<?, ?> map ? castMap(map) : Map.of("detail", String.valueOf(detail))));
        log.setOperatorId(operatorId);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static Map<String, Object> field(String field, String label, String type, String unit, Object options) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("label", label);
        item.put("type", type);
        item.put("unit", unit);
        item.put("options", options);
        return item;
    }

    private static Map<String, Object> pageResult(List<?> list, long total, int pageNum, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
