package com.hmc.his.service.impl;

import com.hmc.his.common.BusinessException;
import com.hmc.his.dto.LoginReq;
import com.hmc.his.model.SysUser;
import com.hmc.his.repository.SysUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 失败分支测试。
 *
 * 注意：登录成功路径会调 {@code StpUtil.login()} 改全局 ThreadLocal，
 * 在纯单元测试里隔离比较麻烦——这种"涉及静态/全局状态"的成功路径
 * 更适合用 {@code @SpringBootTest} 集成测试覆盖。
 *
 * 下面只测两个失败路径，重点演示"用 Mockito 隔离外部依赖"。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private SysUserRepository sysUserRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks private AuthServiceImpl authService;

    @Test
    @DisplayName("用户名不存在时抛 BusinessException")
    void login_userNotFound_throws() {
        when(sysUserRepository.selectByUsername("ghost")).thenReturn(null);

        LoginReq req = new LoginReq();
        req.setUsername("ghost");
        req.setPassword("123456");

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("密码错误时抛 BusinessException")
    void login_wrongPassword_throws() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("hashed-real-password");
        user.setEnabled(true);

        when(sysUserRepository.selectByUsername("admin")).thenReturn(user);
        // 让 mock 的 encoder 返回 false 表示密码不匹配
        when(passwordEncoder.matches("wrong", "hashed-real-password")).thenReturn(false);

        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("密码错误");
    }

    @Test
    @DisplayName("账户被禁用时抛 BusinessException")
    void login_disabledUser_throws() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setEnabled(false);   // 关键：禁用
        when(sysUserRepository.selectByUsername("admin")).thenReturn(user);

        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("123456");

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("禁用");
    }
}
