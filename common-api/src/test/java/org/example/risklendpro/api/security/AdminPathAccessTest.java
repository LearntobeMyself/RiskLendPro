package org.example.risklendpro.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPathAccessTest {

    @Test
    void borrowerCannotHitAdmin() {
        assertFalse(AdminPathAccess.allows("/api/v1/admin/risk/list", "GET", StaffRoles.USER));
    }

    @Test
    void sysAdminCannotApprove() {
        assertFalse(AdminPathAccess.allows("/api/v1/admin/risk/approve", "POST", StaffRoles.SYS_ADMIN));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/loan/approve", "POST", StaffRoles.SYS_ADMIN));
    }

    @Test
    void riskManagerCanApproveAndCollectorCannot() {
        assertTrue(AdminPathAccess.allows("/api/v1/admin/risk/approve", "POST", StaffRoles.RISK_MANAGER));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/risk/approve", "POST", StaffRoles.COLLECTOR));
    }

    @Test
    void auditorReadOnly() {
        assertTrue(AdminPathAccess.allows("/api/v1/admin/risk/list", "GET", StaffRoles.AUDITOR));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/risk/approve", "POST", StaffRoles.AUDITOR));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/system/operation-logs", "GET", StaffRoles.AUDITOR));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/system/config", "PUT", StaffRoles.AUDITOR));
    }

    @Test
    void collectorOverdueAndRiskOverdueRead() {
        assertTrue(AdminPathAccess.allows("/api/v1/admin/repayment/overdue-stats", "GET", StaffRoles.COLLECTOR));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/repayment/reminders", "POST", StaffRoles.COLLECTOR));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/repayment/overdue-stats", "GET", StaffRoles.RISK_MANAGER));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/repayment/reminders", "POST", StaffRoles.RISK_MANAGER));
    }

    @Test
    void staffCannotCallBorrowerApi() {
        assertFalse(AdminPathAccess.allows("/api/v1/loan/request", "POST", StaffRoles.RISK_MANAGER));
        assertTrue(AdminPathAccess.allows("/api/v1/loan/request", "POST", StaffRoles.USER));
    }

    @Test
    void legacyAdminJwtMapsToSysAdmin() {
        assertTrue(AdminPathAccess.allows("/api/v1/admin/users", "GET", "ADMIN"));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/risk/approve", "POST", "ADMIN"));
    }

    @Test
    void sysAdminCreditReadOnlyAndCannotSeeRiskSummaryPlans() {
        assertTrue(AdminPathAccess.allows("/api/v1/admin/credit/limits", "GET", StaffRoles.SYS_ADMIN));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/credit/limits/1/adjust", "POST", StaffRoles.SYS_ADMIN));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/repayment/summary", "GET", StaffRoles.RISK_MANAGER));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/register", "POST", StaffRoles.USER));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/register", "POST", StaffRoles.SYS_ADMIN));
    }

    /** 回归：SYS_ADMIN 必须能读操作日志（AdminSystemController 上标的是 audit:read）。 */
    @Test
    void sysAdminCanReadOperationLogs() {
        assertTrue(AdminPathAccess.allows("/api/v1/admin/system/operation-logs", "GET", StaffRoles.SYS_ADMIN));
        assertTrue(RolePermissions.authorities(StaffRoles.SYS_ADMIN).contains(Permissions.AUDIT_READ));
    }

    @Test
    void productCatalogAdminAccess() {
        assertTrue(AdminPathAccess.allows("/api/v1/admin/products", "GET", StaffRoles.SYS_ADMIN));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/products", "POST", StaffRoles.SYS_ADMIN));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/products/knowledge/export", "GET", StaffRoles.SYS_ADMIN));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/products", "GET", StaffRoles.RISK_MANAGER));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/products", "POST", StaffRoles.RISK_MANAGER));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/products", "GET", StaffRoles.CS_AGENT));
        assertTrue(AdminPathAccess.allows("/api/v1/admin/products", "GET", StaffRoles.AUDITOR));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/products/knowledge/export", "GET", StaffRoles.AUDITOR));
        assertTrue(AdminPathAccess.allows("/api/v1/loan/products", "GET", StaffRoles.USER));
        assertFalse(AdminPathAccess.allows("/api/v1/admin/products", "GET", StaffRoles.USER));
        assertFalse(AdminPathAccess.allows("/api/v1/loan/products", "GET", StaffRoles.SYS_ADMIN));
    }
}
