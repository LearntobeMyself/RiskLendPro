package org.example.risklendpro.user.system;

import org.example.risklendpro.user.admin.AdminCreateRequest;
import org.example.risklendpro.user.admin.AdminUpdateRequest;
import org.example.risklendpro.user.system.SystemConfigUpdateRequest;

import java.util.Map;

public interface AdminSystemService {

    Map<String, Object> listAdmins(Integer page, Integer size);

    Map<String, Object> createAdmin(AdminCreateRequest request);

    Map<String, Object> updateAdmin(Long adminId, AdminUpdateRequest request);

    void deleteAdmin(Long adminId);

    Map<String, Object> listOperationLogs(Integer page, Integer size, String module, String startDate, String endDate);

    Map<String, Object> getConfig();

    Map<String, Object> updateConfig(SystemConfigUpdateRequest request);

    Map<String, Object> listBackups(Integer page, Integer size);

    Map<String, Object> createBackup();

    Map<String, Object> restoreBackup(String backupId);

    byte[] downloadBackup(String backupId);
}
