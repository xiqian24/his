package com.hmc.his.service.impl;

import com.hmc.his.common.BusinessException;
import com.hmc.his.dto.RegistrationCreateReq;
import com.hmc.his.model.Doctor;
import com.hmc.his.model.Patient;
import com.hmc.his.model.Registration;
import com.hmc.his.repository.RegistrationRepository;
import com.hmc.his.service.DoctorService;
import com.hmc.his.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RegistrationServiceImpl 的单元测试 —— Mockito 用法演示。
 *
 * 关键概念：
 *   • {@code @ExtendWith(MockitoExtension.class)} 启用 Mockito 注解处理
 *   • {@code @Mock} 创建 mock 对象（假的依赖）
 *   • {@code @InjectMocks} 把 mock 注入被测对象
 *   • {@code when(...).thenReturn(...)} 设定 mock 行为
 *   • {@code verify(...)} 验证 mock 是否被以期望方式调用
 *   • {@code ArgumentCaptor} 抓住调用时的参数对象，做更细粒度的断言
 *
 * 这种"纯单元测试"完全不启动 Spring，跑得飞快（毫秒级）。
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock private RegistrationRepository registrationRepository;
    @Mock private PatientService patientService;
    @Mock private DoctorService doctorService;

    @InjectMocks private RegistrationServiceImpl registrationService;

    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setId(10L);
        patient.setName("张三");

        doctor = new Doctor();
        doctor.setId(20L);
        doctor.setDeptId(1L);
        doctor.setRegFee(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("create() 正常路径：装配字段、生成 reg_no、状态置 WAITING")
    void create_happyPath_buildsRegistrationCorrectly() {
        // ── arrange ────────────────────────────────────────────────
        when(patientService.get(10L)).thenReturn(patient);
        when(doctorService.get(20L)).thenReturn(doctor);
        when(registrationRepository.countByDate(any())).thenReturn(0L);

        // 模拟 MyBatis @Options(useGeneratedKeys) 的行为：insert 后回填 id
        doAnswer(inv -> {
            ((Registration) inv.getArgument(0)).setId(100L);
            return 1;
        }).when(registrationRepository).insert(any(Registration.class));

        Registration full = new Registration();
        full.setId(100L);
        full.setStatus("WAITING");
        when(registrationRepository.selectById(100L)).thenReturn(full);

        // ── act ────────────────────────────────────────────────────
        RegistrationCreateReq req = new RegistrationCreateReq();
        req.setPatientId(10L);
        req.setDoctorId(20L);
        Registration result = registrationService.create(req);

        // ── assert ─────────────────────────────────────────────────
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo("WAITING");

        // 抓住 insert 时传的 Registration 对象，逐字段验证
        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).insert(captor.capture());
        Registration inserted = captor.getValue();

        assertThat(inserted.getPatientId()).isEqualTo(10L);
        assertThat(inserted.getDoctorId()).isEqualTo(20L);
        assertThat(inserted.getDeptId()).isEqualTo(1L);
        assertThat(inserted.getRegFee()).isEqualByComparingTo("15.00");
        assertThat(inserted.getStatus()).isEqualTo("WAITING");
        assertThat(inserted.getRegDate()).isEqualTo(LocalDate.now());

        // reg_no 格式：R + yyyyMMdd + 4 位序号（首单 0001）
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(inserted.getRegNo()).isEqualTo("R" + today + "0001");
    }

    @Test
    @DisplayName("cancel() 当状态为 WAITING 时，应置为 CANCELLED")
    void cancel_whenStatusWaiting_setsCancelled() {
        Registration reg = new Registration();
        reg.setId(100L);
        reg.setStatus("WAITING");
        when(registrationRepository.selectById(100L)).thenReturn(reg);

        registrationService.cancel(100L);

        verify(registrationRepository).updateStatus(100L, "CANCELLED");
    }

    @Test
    @DisplayName("cancel() 当状态为 VISITING 时，抛 BusinessException 且不改库")
    void cancel_whenStatusVisiting_throwsBusinessException() {
        Registration reg = new Registration();
        reg.setId(100L);
        reg.setStatus("VISITING");
        when(registrationRepository.selectById(100L)).thenReturn(reg);

        assertThatThrownBy(() -> registrationService.cancel(100L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅待诊状态");

        // never() 验证 updateStatus 一次都没被调用
        verify(registrationRepository, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("changeStatus() 拒绝非法状态值")
    void changeStatus_invalidStatus_throwsException() {
        assertThatThrownBy(() -> registrationService.changeStatus(1L, "FOO"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非法状态");
    }
}
