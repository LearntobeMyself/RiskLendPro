package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.pojo.response.UserCreditLimitResponse;
import org.example.risklendpro.service.UserCreditLimitService;
import org.example.risklendpro.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "贷后监控与额度管理模块", description = "额度管理相关接口")
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserCreditLimitService userCreditLimitService;

    @Operation(summary = "查看用户额度", description = "用户查看自己的当前总额度、已用额度、剩余额度")
    @GetMapping("/credit-limit")
    public CommonResponse<UserCreditLimitResponse> creditLimit(HttpServletRequest request) {
        Long userId = SecurityUtils.getUserIdFromRequest(request);
        UserCreditLimitResponse response = userCreditLimitService.getUserCreditLimit(userId);
        return CommonResponse.success("获取额度信息成功", response);
    }
}
