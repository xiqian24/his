package com.hmc.his.service.impl;

import com.hmc.his.dto.PrescriptionCreateReq;
import com.hmc.his.model.Drug;
import com.hmc.his.model.Prescription;
import com.hmc.his.model.PrescriptionItem;
import com.hmc.his.model.Visit;
import com.hmc.his.repository.PrescriptionRepository;
import com.hmc.his.repository.VisitRepository;
import com.hmc.his.service.DrugService;
import com.hmc.his.service.RegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VisitServiceImpl 测试 —— 演示 ArgumentCaptor 抓集合调用 + 业务规则断言。
 *
 * 这里要保护的核心业务规则：
 *   • 处方金额必须由服务端计算，绝不接受前端传入
 *   • prescription.total_amount = ∑ prescription_item.subtotal
 *   • subtotal = drug.price * qty
 *
 * 这种"金额一旦算错就会造成实际损失"的逻辑特别值得写测试。
 */
@ExtendWith(MockitoExtension.class)
class VisitServiceImplTest {

    @Mock private VisitRepository visitRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private RegistrationService registrationService;
    @Mock private DrugService drugService;
    @InjectMocks private VisitServiceImpl visitService;

    @Test
    @DisplayName("prescribe() 应在服务端计算 subtotal 与 total_amount")
    void prescribe_calculatesSubtotalAndTotal() {
        // ── arrange: 一次就诊，开两种药 ─────────────────────────
        Visit visit = new Visit();
        visit.setId(1L);
        when(visitRepository.selectById(1L)).thenReturn(visit);

        Drug aspirin = drugWithPrice(101L, "10.00");
        Drug vitC    = drugWithPrice(102L, "5.50");
        when(drugService.get(101L)).thenReturn(aspirin);
        when(drugService.get(102L)).thenReturn(vitC);

        // 模拟 insert 时回填自增 id
        doAnswer(inv -> {
            ((Prescription) inv.getArgument(0)).setId(900L);
            return 1;
        }).when(prescriptionRepository).insert(any(Prescription.class));

        // ── act: 前端只传 drugId / qty / days / 用法，金额不传 ─
        PrescriptionCreateReq req = new PrescriptionCreateReq();
        PrescriptionCreateReq.Item it1 = new PrescriptionCreateReq.Item();
        it1.setDrugId(101L); it1.setQty(2); it1.setDays(3); it1.setDosage("0.25g"); it1.setFrequency("一日三次");
        PrescriptionCreateReq.Item it2 = new PrescriptionCreateReq.Item();
        it2.setDrugId(102L); it2.setQty(1); it2.setDays(7);
        req.setItems(List.of(it1, it2));

        Prescription result = visitService.prescribe(1L, req);

        // ── assert: 总额 = 10.00*2 + 5.50*1 = 25.50 ───────────
        assertThat(result.getTotalAmount()).isEqualByComparingTo("25.50");

        // 抓住每次 insertItem 的参数，验证 unit_price/subtotal 都是服务端算的
        ArgumentCaptor<PrescriptionItem> captor = ArgumentCaptor.forClass(PrescriptionItem.class);
        verify(prescriptionRepository, times(2)).insertItem(captor.capture());
        List<PrescriptionItem> items = captor.getAllValues();

        // 第一条：阿司匹林，10.00 * 2 = 20.00
        assertThat(items.get(0).getDrugId()).isEqualTo(101L);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(items.get(0).getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(items.get(0).getPrescriptionId()).isEqualTo(900L);
        assertThat(items.get(0).getDosage()).isEqualTo("0.25g");

        // 第二条：维生素 C，5.50 * 1 = 5.50
        assertThat(items.get(1).getDrugId()).isEqualTo(102L);
        assertThat(items.get(1).getSubtotal()).isEqualByComparingTo("5.50");

        // 最后一定要落库 totalAmount，且金额正确
        verify(prescriptionRepository).updateTotal(eq(900L),
                argThat(amt -> amt.compareTo(new BigDecimal("25.50")) == 0));
    }

    private static Drug drugWithPrice(Long id, String price) {
        Drug d = new Drug();
        d.setId(id);
        d.setPrice(new BigDecimal(price));
        return d;
    }
}
