package com.hmc.his.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务异常类的单元测试。
 *
 * 异常类看起来很简单，但写测试依然有价值：
 *   • 防止以后有人改坏默认 code（如把 400 改成 500）
 *   • 让阅读测试的人立刻知道这个类的契约
 */
class BusinessExceptionTest {

    @Test
    void defaultConstructor_codeIs400() {
        BusinessException e = new BusinessException("用户不存在");

        assertThat(e.getCode()).isEqualTo(400);
        assertThat(e.getMessage()).isEqualTo("用户不存在");
    }

    @Test
    void customCode_isPreserved() {
        BusinessException e = new BusinessException(404, "未找到");

        assertThat(e.getCode()).isEqualTo(404);
        assertThat(e.getMessage()).isEqualTo("未找到");
    }
}
