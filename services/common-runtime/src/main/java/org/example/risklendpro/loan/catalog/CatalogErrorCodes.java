package org.example.risklendpro.loan.catalog;

public final class CatalogErrorCodes {

    public static final int PARAM_INVALID = 40001;
    public static final int RANGE_INVALID = 40002;
    public static final int EXTRA_FIELD_INVALID = 40003;
    public static final int TAG_CATEGORY_MISMATCH = 40004;
    public static final int IMAGE_INVALID = 40005;
    public static final int IMAGE_NOT_UPLOADED = 40006;
    public static final int CATEGORY_RULE_CONFLICT = 40007;
    public static final int FIELD_DEPENDENCY_INVALID = 40008;
    public static final int UNAUTHORIZED = 40101;
    public static final int FORBIDDEN = 40301;
    public static final int PRODUCT_NOT_FOUND = 40401;
    public static final int INSTITUTION_NOT_FOUND = 40402;
    public static final int TAG_NOT_FOUND = 40403;
    public static final int PRODUCT_NAME_DUPLICATE = 40901;
    public static final int INSTITUTION_NAME_DUPLICATE = 40902;
    public static final int VERSION_CONFLICT = 40903;
    public static final int PRODUCT_OFF_SHELF = 40904;
    public static final int PRODUCT_CANNOT_DELETE = 40905;
    public static final int TAG_IN_USE = 40906;
    public static final int ON_SHELF_CHECK_FAILED = 42201;
    public static final int STORAGE_ERROR = 50001;

    private CatalogErrorCodes() {
    }
}
