# 14 · 单元测试与 Mock

> 杭州医学院 门诊 HIS 系统 · 实训文档

本文档教你**为什么测、测什么、怎么测**。所有示例都已经写在 `backend/src/test/java/` 下，可以直接跑、直接看。

---

## 1. 为什么要写测试

> "我手动点几下能跑通就行，写测试好麻烦"

短期看是快，但项目稍微大一点就会爆雷：

| 没测试 | 有测试 |
| --- | --- |
| 改了 A 模块，B 模块悄悄坏了，上线才发现 | CI 立刻红灯，1 分钟内知道 |
| 一个隐蔽 bug 调试半天 | 一个失败的单元测试直接定位 |
| 重构不敢动，因为"动了不知道哪里炸" | 测试是安全网，重构才有底气 |
| 文档写得再好也会过期 | 测试是"活的文档"——一段能跑的契约 |

实训项目里把测试代码当成**技能点**练熟，是工程师从"会写代码"过渡到"会写**可维护的**代码"的必经之路。

---

## 2. 三层测试一张图

```
       ┌─────────────────────────────────────────┐
       │  集成测试 / 端到端  (Integration / E2E) │  慢、少、覆盖关键链路
       │    @SpringBootTest + 真 DB / Testcontainers
       └─────────────────────────────────────────┘
       ┌─────────────────────────────────────────┐
       │  切片测试 (Slice)                       │  中等
       │    @WebMvcTest / @MybatisTest / MockMvc │
       │    只启动一层 Spring 上下文              │
       └─────────────────────────────────────────┘
       ┌─────────────────────────────────────────┐
       │  单元测试 (Unit)                        │  快、多、占比最大
       │    JUnit 5 + Mockito，纯 Java，毫秒级    │
       └─────────────────────────────────────────┘
```

**经验法则：单元测试 ≈ 70%，切片测试 ≈ 20%，集成测试 ≈ 10%**。本项目目前覆盖前两层，集成测试留给学生作为扩展。

---

## 3. JUnit 5 三分钟入门

打开 `backend/src/test/java/com/hmc/his/common/RTest.java`：

```java
@Test                              // 标注这是个测试方法
@DisplayName("R.ok(data) 应该返回 code=0")  // 报告里显示的人话标题
void okWithData_setsCodeZeroAndData() {
    R<String> r = R.ok("hello");          // ① arrange + act
    
    assertThat(r.getCode()).isEqualTo(0); // ② assert
    assertThat(r.getMsg()).isEqualTo("ok");
}
```

JUnit 5 常用注解：

| 注解 | 时机 | 用法 |
| --- | --- | --- |
| `@Test` | 标注测试方法 | 必加 |
| `@BeforeEach` | 每个测试方法**之前**跑 | 准备公共 fixture |
| `@AfterEach` | 每个测试方法**之后**跑 | 清理资源 |
| `@BeforeAll` | 整个类**只跑一次**（要 static） | 重型初始化 |
| `@DisplayName` | 给测试起一个人能看懂的名字 | 报告更清晰 |
| `@Disabled("原因")` | 暂时跳过这个测试 | 临时禁用 |
| `@ParameterizedTest` | 跑多组参数 | 数据驱动 |

断言推荐用 **AssertJ**（链式调用，可读性比 JUnit 自带的 `assertEquals` 强很多）：

```java
assertThat(actual).isEqualTo(expected);
assertThat(list).hasSize(3).contains("a", "b");
assertThat(amount).isEqualByComparingTo("25.50");   // BigDecimal 比较
assertThatThrownBy(() -> service.foo())
    .isInstanceOf(BusinessException.class)
    .hasMessageContaining("不存在");
```

---

## 4. Mockito 五分钟入门

### 4.1 为什么需要 Mock

测 Service 时如果直连 Repository，就会涉及数据库。这有几个问题：

- **慢** —— 一个测试方法启动 Spring + 连 DB 几秒钟，几百个测试就要几分钟
- **不稳** —— 数据脏了/网络抖动会让测试时灵时不灵
- **耦合** —— 测 Service 的同时也测了 Repository，Service 测试因 Repo bug 失败

**Mock = 假货依赖**。我们告诉假货"被这样调用就这样回答"，于是被测代码以为自己在跟真依赖打交道，但全程没碰数据库。

### 4.2 最小骨架

`backend/src/test/java/com/hmc/his/service/impl/RegistrationServiceImplTest.java`：

```java
@ExtendWith(MockitoExtension.class)               // ① 启用 Mockito 注解处理
class RegistrationServiceImplTest {

    @Mock private RegistrationRepository registrationRepository;  // ② 创建 mock
    @Mock private PatientService patientService;
    @Mock private DoctorService doctorService;

    @InjectMocks private RegistrationServiceImpl registrationService;  // ③ 把 mock 注入到被测对象

    @Test
    void cancel_whenStatusVisiting_throwsBusinessException() {
        // ── arrange: 设定 mock 行为 ────────────────
        Registration reg = new Registration();
        reg.setStatus("VISITING");
        when(registrationRepository.selectById(100L)).thenReturn(reg);

        // ── act + assert: 调用被测代码并验证抛异常 ──
        assertThatThrownBy(() -> registrationService.cancel(100L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅待诊状态");

        // ── assert: 验证 mock 没被错调用 ────────────
        verify(registrationRepository, never()).updateStatus(anyLong(), anyString());
    }
}
```

### 4.3 Mockito 常用 API

```java
// 设定行为
when(mock.foo(arg)).thenReturn(value);
when(mock.foo(arg)).thenThrow(new RuntimeException());

// 不返回值的方法
doNothing().when(mock).voidMethod();
doThrow(...).when(mock).voidMethod();

// 自定义行为（比如修改入参，模拟 MyBatis 自增 id 回填）
doAnswer(inv -> {
    ((User) inv.getArgument(0)).setId(100L);
    return 1;
}).when(mock).insert(any(User.class));

// 验证调用
verify(mock).foo(arg);                    // 必须调用过一次
verify(mock, times(2)).foo(any());        // 调过 2 次
verify(mock, never()).foo(any());         // 一次也没调
verify(mock, atLeastOnce()).foo(any());

// 参数匹配器
any(), any(String.class), eq(5), anyLong(), argThat(x -> x > 0)
```

### 4.4 ArgumentCaptor —— 抓住调用参数做细粒度断言

这个用得熟以后，调试效率会翻倍。打开 `VisitServiceImplTest.java` 看：

```java
ArgumentCaptor<PrescriptionItem> captor = ArgumentCaptor.forClass(PrescriptionItem.class);
verify(prescriptionRepository, times(2)).insertItem(captor.capture());
List<PrescriptionItem> items = captor.getAllValues();

// 第一次调用 insertItem 时传的是阿司匹林，10.00 * 2 = 20.00
assertThat(items.get(0).getDrugId()).isEqualTo(101L);
assertThat(items.get(0).getSubtotal()).isEqualByComparingTo("20.00");
```

这就把"金额是不是服务端算的、算得对不对"这条业务规则用代码锁死了——以后**任何人**改坏 `prescribe()` 的金额计算，CI 都会立刻红灯。

---

## 5. MockMvc：测 HTTP 层（不连数据库）

`AuthControllerTest.java` 演示了三种典型场景：

```java
@BeforeEach
void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
}

@Test
void login_success_returnsToken() throws Exception {
    when(authService.login(any())).thenReturn(fakeRes);

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.tokenValue").value("fake-token-uuid"));
}
```

测的是：**给定一个 HTTP 请求 + 假的 Service，Controller 是否生成正确的响应**。

`jsonPath("$.code")` 是 JSON 路径表达式：
- `$.code` —— 顶层 code 字段
- `$.data.userInfo.role` —— 嵌套字段
- `$.data.records[0].name` —— 数组第一项

### 5.1 几种 MockMvc 写法对比

| 写法 | 启动什么 | 速度 | 用途 |
| --- | --- | --- | --- |
| `MockMvcBuilders.standaloneSetup(controller)` | 只这一个 controller + 你显式注册的 advice | 最快 | 单纯测 controller 逻辑 |
| `@WebMvcTest(SomeController.class)` | Spring MVC 切片 + 这个 controller | 中 | 想验证全局过滤器、HandlerInterceptor |
| `@SpringBootTest + @AutoConfigureMockMvc` | 整个 Spring 上下文 | 慢 | 端到端验证 |

本项目教学用 `standaloneSetup`，简单直接。

---

## 6. 怎么跑测试

### 命令行

```bash
cd backend

# 跑所有测试
./gradlew test

# 跑某一个测试类
./gradlew test --tests com.hmc.his.service.impl.RegistrationServiceImplTest

# 跑某一个测试方法
./gradlew test --tests com.hmc.his.service.impl.RegistrationServiceImplTest.cancel_whenStatusVisiting_throwsBusinessException

# 看详细输出（成功/失败的方法名）
./gradlew test --info
```

跑完后看报告：`backend/build/reports/tests/test/index.html`。

### IntelliJ IDEA

最舒服的方式：

1. 测试类左边行号旁边有绿三角 → 点击 → "Run/Debug"
2. 单个测试方法旁也有 → 单跑这一个方法
3. 在测试方法里打断点，按虫子图标 Debug 跑——可以一步步看 mock 行为
4. 跑完左下角 Run 面板会显示测试树，绿色 ✓ / 红色 ✗ 一目了然

### CI

每次 push 到 main，`.github/workflows/ci.yml` 会自动跑 `./gradlew test`。失败会在 PR 上红灯阻止合并。

---

## 7. 项目里现有的测试一览

| 文件 | 测什么 | 演示的技能 |
| --- | --- | --- |
| `common/RTest.java` | `R<T>` 工具类 | 最简单的纯单元测试入门 |
| `common/BusinessExceptionTest.java` | 业务异常类 | 异常类的契约保护 |
| `service/impl/RegistrationServiceImplTest.java` | 挂号 Service | Mockito 全套：@Mock / @InjectMocks / when / verify / ArgumentCaptor / never |
| `service/impl/VisitServiceImplTest.java` | 处方 Service | ArgumentCaptor 抓集合参数 + BigDecimal 金额断言 |
| `service/impl/AuthServiceImplTest.java` | 登录 Service | mock 第三方依赖（PasswordEncoder） |
| `controller/AuthControllerTest.java` | 登录 HTTP 接口 | MockMvc + jsonPath + 全局异常处理 |
| `HisApplicationTests.java` | （占位） | 留着以后扩展为 @SpringBootTest |

---

## 8. 怎么给自己的新模块加测试

假设你做完 [13-实战 检查申请模块](./13-实战-添加检查申请模块.md) 的练习，要给它写测试：

### 第一步：写 ServiceImpl 的单元测试

最有价值。复制 `VisitServiceImplTest.java` 的骨架，把：

```java
@Mock private ExamOrderRepository examOrderRepository;
@InjectMocks private ExamOrderServiceImpl examOrderService;
```

测试每个公开方法的：
- 正常路径（输入合法 → 调了正确的 Repository 方法 → 返回正确的值）
- 异常路径（输入非法 / 状态非法 → 抛 BusinessException 且不写库）

### 第二步：写 Controller 的 MockMvc 测试

复制 `AuthControllerTest.java` 骨架，把每个接口的：
- 成功响应（200 + code=0）
- 参数校验失败（code=400）
- Service 抛业务异常（code=400）

各覆盖一遍。

### 第三步（可选）：写集成测试

需要真数据库，改用 `@SpringBootTest`。可以参考 `HisApplicationTests.java` 扩展。

---

## 9. 写好测试的几条铁律

1. **一个测试方法只测一件事**。失败时立刻知道哪条规则坏了。
2. **测试方法名要"自带断言"**：`cancel_whenStatusVisiting_throwsBusinessException` 比 `testCancel` 好太多。
3. **AAA 结构**：Arrange（准备） → Act（行动） → Assert（断言）。代码里加个空行视觉分块。
4. **不要测 framework**：不测 Lombok 生成的 setter、不测 Spring 自动注入。专注业务规则。
5. **测试要确定性**：避免 `LocalDateTime.now()` 这种依赖时间的代码（必要时用 `Clock` 做注入），避免依赖跨测试方法顺序。
6. **保护"会出问题"的代码**：状态机、金额计算、权限判断、并发逻辑，必写测试。简单的 getter/setter 不用。
7. **测试也要重构**：重复代码抽出来到 `@BeforeEach` 或私有 helper 方法。

---

## 10. 常见踩坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| `Cannot mock final class` | 旧版 Mockito 不能 mock final 类 | spring-boot-starter-test 的 Mockito 已经支持，确认依赖 |
| `UnnecessaryStubbingException` | 设了 `when()` 但被测代码没调用 | 删掉那一行 stub，或确认调用路径 |
| `MockitoException: NullPointerException` | `@InjectMocks` 注入失败（构造器签名变了） | 检查被测类构造器，所有 @Mock 字段类型要对得上 |
| `assertThrows` 没抛异常但测试通过 | 用 `assertThat` 代替了 `assertThatThrownBy` | 抛异常断言一定要用 `assertThatThrownBy` |
| BigDecimal 比较 `isEqualTo` 失败 | scale 不一致（10 vs 10.00） | 用 `isEqualByComparingTo("10.00")` |
| 测试在 IDEA 通过、命令行失败 | 测试间隐式依赖（共享 static 状态、文件） | 每个测试自己 setUp 自己的 fixture |

---

## 11. 实训作业建议

每完成一个新模块，**至少**补：

- ✅ ServiceImpl 的 Mockito 单元测试（覆盖核心业务规则）
- ✅ Controller 的 MockMvc 测试（覆盖参数校验和业务异常路径）
- 推荐：1 条端到端集成测试（@SpringBootTest，真 DB）

把测试覆盖率（Coverage）做到 70%+ 是合格的。命令行看覆盖率：

```bash
./gradlew test jacocoTestReport
# 报告生成在 backend/build/reports/jacoco/test/html/index.html
```

> Jacoco 不是项目默认依赖。要启用，在 `build.gradle` 加 `id 'jacoco'` 插件，详见 Gradle 官方文档。

---

## 下一步

- 看代码里现成的测试案例：`backend/src/test/java/com/hmc/his/`
- 实战：[13-实战-添加检查申请模块](./13-实战-添加检查申请模块.md)，做完后给它补测试
- 调试技巧：[10-IDE-与调试-Git](./10-IDE-与调试-Git.md)
