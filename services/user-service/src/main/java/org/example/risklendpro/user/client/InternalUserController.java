package org.example.risklendpro.user.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.UserQueryApi;
import org.example.risklendpro.api.dto.AdminProfile;
import org.example.risklendpro.api.dto.UserAssessmentStatusCommand;
import org.example.risklendpro.api.dto.UserDetail;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.user.entity.Admin;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.user.mapper.AdminMapper;
import org.example.risklendpro.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * user-service 对外的只读/状态回写契约实现，供 loan/risk 通过 Feign 消费。
 */
@RestController
public class InternalUserController implements UserQueryApi {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AdminMapper adminMapper;

    @Override
    public UserSummary getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return toSummary(user);
    }

    @Override
    public UserDetail getUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return new UserDetail(
                user.getId(),
                user.getRealName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getIdCard(),
                user.getAccountStatus(),
                user.getAssessmentStatus(),
                user.getCreateTime() == null ? null : user.getCreateTime().getTime()
        );
    }

    @Override
    public List<Long> searchUserIdsByName(String name) {
        return userMapper.selectList(
                        new QueryWrapper<User>().like("real_name", name)
                ).stream()
                .map(User::getId)
                .toList();
    }

    @Override
    public AdminProfile getAdmin(Long adminId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            return null;
        }
        return new AdminProfile(admin.getId(), admin.getUsername());
    }

    @Override
    public void updateAssessmentStatus(UserAssessmentStatusCommand command) {
        User user = userMapper.selectById(command.userId());
        if (user == null) {
            return;
        }
        user.setAssessmentStatus(command.status());
        userMapper.updateById(user);
    }

    private UserSummary toSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getRealName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getIdCard(),
                user.getAccountStatus(),
                user.getAssessmentStatus(),
                user.getCreateTime() == null ? null : user.getCreateTime().getTime()
        );
    }
}
