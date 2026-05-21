package com.hmc.his.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmc.his.common.BusinessException;
import com.hmc.his.common.GlobalExceptionHandler;
import com.hmc.his.dto.LoginReq;
import com.hmc.his.dto.LoginRes;
import com.hmc.his.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 的 HTTP 层测试。
 *
 * 这里用 {@code MockMvcBuilders.standaloneSetup} 直接构造一个 MockMvc，
 * 不启动整个 Spring 上下文，也不走 Sa-Token 拦截器 ——
 * 只测 controller 与 service 的契约（参数校验 / 状态码 / JSON 形态）。
 *
 * 比起 {@code @WebMvcTest} 与 {@code @SpringBootTest}，这种写法启动最快，
 * 跑几百个测试只需要几秒。代价是隔离更彻底，全局配置不生效。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @InjectMocks private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())   // 让全局异常处理器参与
                .build();
    }

    @Test
    @DisplayName("登录成功时返回 code=0 和 token")
    void login_success_returnsToken() throws Exception {
        LoginRes res = new LoginRes();
        res.setTokenName("Authorization");
        res.setTokenValue("fake-token-uuid");

        LoginRes.UserInfo info = new LoginRes.UserInfo();
        info.setId(1L);
        info.setUsername("admin");
        info.setRealName("系统管理员");
        info.setRole("ADMIN");
        res.setUserInfo(info);

        when(authService.login(any())).thenReturn(res);

        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("123456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenValue").value("fake-token-uuid"))
                .andExpect(jsonPath("$.data.userInfo.role").value("ADMIN"));
    }

    @Test
    @DisplayName("用户名为空时被参数校验拦下，返回 code=400")
    void login_blankUsername_returns400() throws Exception {
        LoginReq req = new LoginReq();
        req.setUsername("");           // @NotBlank 会拒绝
        req.setPassword("123456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("Service 抛 BusinessException 时被全局异常处理转成 R{code:400}")
    void login_businessError_returnsR400() throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException("密码错误"));

        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("密码错误"));
    }
}
