package com.nutriapp.modules.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import com.nutriapp.modules.receta.service.RecetaService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock RecetaRepository recetaRepository;
    @Mock RecetaService recetaService;
    @Mock NutricionistaService nutricionistaService;
    @Mock PacienteRepository pacienteRepository;

    @InjectMocks DashboardService service;

    private Nutricionista nutri;

    @BeforeEach
    void setup() {
        nutri = new Nutricionista();
        nutri.setId(UUID.randomUUID());
        when(nutricionistaService.getCurrent()).thenReturn(nutri);
        when(recetaRepository.sumVentasEntre(any(), any(), any())).thenReturn(new BigDecimal("55930.00"));
        when(recetaRepository.sumComisionEntre(any(), any(), any())).thenReturn(new BigDecimal("5593.00"));
        when(recetaRepository.findAplicadasEntre(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void cierreMensual_calculaTasaConversion() {
        when(recetaRepository.countEmitidasEntre(eq(nutri.getId()), any(), any())).thenReturn(10L);
        when(recetaRepository.countAplicadasEntre(eq(nutri.getId()), eq(EstadoReceta.APLICADA), any(), any()))
                .thenReturn(3L);

        var resp = service.cierreMensual(2026, 7);

        assertThat(resp.recetasEmitidas()).isEqualTo(10);
        assertThat(resp.recetasAplicadas()).isEqualTo(3);
        assertThat(resp.tasaConversion()).isEqualByComparingTo("0.30");
        assertThat(resp.ventasGeneradas()).isEqualByComparingTo("55930.00");
        assertThat(resp.comisionTotal()).isEqualByComparingTo("5593.00");
    }

    @Test
    void cierreMensual_sinEmitidas_tasaCero_sinDivisionPorCero() {
        when(recetaRepository.countEmitidasEntre(eq(nutri.getId()), any(), any())).thenReturn(0L);
        when(recetaRepository.countAplicadasEntre(any(), any(), any(), any())).thenReturn(0L);

        var resp = service.cierreMensual(2026, 7);

        assertThat(resp.tasaConversion()).isEqualByComparingTo("0");
    }

    @Test
    void cierreMensual_mesInvalido_lanza409() {
        assertThatThrownBy(() -> service.cierreMensual(2026, 13))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cierreMensual_armaDetalleDeConvertidas() {
        Paciente juan = new Paciente();
        juan.setId(UUID.randomUUID());
        juan.setNombre("Juan");
        juan.setApellido("Pérez");

        Receta r = new Receta();
        r.setCodigo("RX-ABC123");
        r.setPacienteId(juan.getId());
        r.setOrdenTotal(new BigDecimal("31500.00"));
        r.setComisionMonto(new BigDecimal("3150.00"));
        r.setOrdenPaidAt(Instant.parse("2026-07-10T12:00:00Z"));

        when(recetaRepository.countEmitidasEntre(any(), any(), any())).thenReturn(1L);
        when(recetaRepository.countAplicadasEntre(any(), any(), any(), any())).thenReturn(1L);
        when(recetaRepository.findAplicadasEntre(eq(nutri.getId()), any(), any())).thenReturn(List.of(r));
        when(pacienteRepository.findById(juan.getId())).thenReturn(Optional.of(juan));

        var resp = service.cierreMensual(2026, 7);

        assertThat(resp.detalle()).hasSize(1);
        var d = resp.detalle().get(0);
        assertThat(d.recetaCodigo()).isEqualTo("RX-ABC123");
        assertThat(d.paciente()).isEqualTo("Juan Pérez");
        assertThat(d.ordenTotal()).isEqualByComparingTo("31500.00");
        assertThat(d.comisionMonto()).isEqualByComparingTo("3150.00");
    }
}
