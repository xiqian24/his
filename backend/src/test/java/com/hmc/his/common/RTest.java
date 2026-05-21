package com.hmc.his.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R&lt;T&gt; 统一响应壳子的单元测试。
 *
 * 这是最简单的"纯单元测试"——不依赖 Spring、不连数据库、不调外部服务，
 * 只测一段纯逻辑。每个测试方法应该只验证一件事，命名清晰。
 *
 * 关键：
 *   • 用 AssertJ 的 assertThat(...) 链式断言，可读性比 JUnit 自带的 assertEquals 强
 *   • 一个测试方法 = 一个独立场景，互不依赖
 */
class RTest {

    @Test
    @DisplayName("R.ok(data) 应该返回 code=0，msg=ok，data 透传")
    void okWithData_setsCodeZeroAndData() {
        R<String> r = R.ok("hello");

        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getMsg()).isEqualTo("ok");
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("R.ok() 不带参数时 data 为 null")
    void okWithoutData_setsDataNull() {
        R<Void> r = R.ok();

        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getData()).isNull();
    }

    @Test
    @DisplayName("R.fail(code, msg) 保留自定义 code 与 msg")
    void fail_setsCodeAndMsg() {
        R<Void> r = R.fail(404, "not found");

        assertThat(r.getCode()).isEqualTo(404);
        assertThat(r.getMsg()).isEqualTo("not found");
        assertThat(r.getData()).isNull();
    }
}
