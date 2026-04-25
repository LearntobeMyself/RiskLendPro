package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "贷后监控与额度管理模块", description = "额度管理相关接口")
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Operation(summary = "查看用户额度", description = "用户查看自己的当前总额度、已用额度、剩余额度")
    @GetMapping("/credit-limit")
    public CommonResponse<Object> creditLimit(@RequestParam Long userId) {
        // 调用service方法
        // return CommonResponse.success(userService.getUserCreditLimit(userId));
        return CommonResponse.success(null);
    }
}