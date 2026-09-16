package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.AdminProfile;
import org.example.risklendpro.api.dto.UserAssessmentStatusCommand;
import org.example.risklendpro.api.dto.UserDetail;
import org.example.risklendpro.api.dto.UserSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户域对外提供的只读/状态回写契约。由 user-service 实现，loan/risk 消费。
 */
public interface UserQueryApi {

    @GetMapping("/internal/users/{userId}")
    UserSummary getUser(@PathVariable("userId") Long userId);

    @GetMapping("/internal/users/{userId}/detail")
    UserDetail getUserDetail(@PathVariable("userId") Long userId);

    @GetMapping("/internal/users/search-by-name")
    List<Long> searchUserIdsByName(@RequestParam("name") String name);

    /** 按姓名和/或手机号组合模糊查询用户 id（两个条件为空则返回空列表）。 */
    @GetMapping("/internal/users/search")
    List<Long> searchUserIds(@RequestParam(value = "name", required = false) String name,
                             @RequestParam(value = "phone", required = false) String phone);

    @GetMapping("/internal/users/count")
    long countUsers();

    @GetMapping("/internal/users/list")
    List<UserSummary> listAllUsers();

    @GetMapping("/internal/admins/{adminId}")
    AdminProfile getAdmin(@PathVariable("adminId") Long adminId);

    @PostMapping("/internal/users/assessment-status")
    void updateAssessmentStatus(@RequestBody UserAssessmentStatusCommand command);
}