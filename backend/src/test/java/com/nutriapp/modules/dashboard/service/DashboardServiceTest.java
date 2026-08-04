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
        when(recetaRepository.sumComisionEntre(any(), any(), any())).thenReturn(new BigDecimal("5593.00"));
        when(recetaRepository.findConvertidasEntre(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void cierreMensual_calculaTasaConversion() {
        when(recetaRepository.countEmitidasEntre(eq(nutri.getId()), any(), any())).thenReturn(10L);
        when(recetaRepository.countConvertidasEntre(eq(nutri.getId()), any(), any()))
                .thenReturn(3L);

        var resp = service.cierreMensual(2026, 7);

        assertThat(resp.recetasEmitidas()).isEqualTo(10);
        assertThat(resp.recetasAplicadas()).isEqualTo(3);
        assertThat(resp.tasaConversion()).isEqualByComparingTo("0.30");
        assertThat(resp.comisionTotal()).isEqualByComparingTo("5593.00");
    }

    @Test
    void cierreMensual_sinEmitidas_tasaCero_sinDivisionPorCero() {
        when(recetaRepository.countEmitidasEntre(eq(nutri.getId()), any(), any())).thenReturn(0L);
        when(recetaRepository.countConvertidasEntre(any(), any(), any())).thenReturn(0L);

        var resp = service.cierreMensual(2026, 7);

        assertThat(resp.tasaConversion()).isEqualByComparingTo("0");
    }

    @Test
    void cierreMensual_mesInvalido_lanza409() {
        assertThatThrownBy(() -> service.cierreMensual(2026, 13))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void estadisticas_devuelveSerieCronologicaDeNMeses() {
        when(recetaRepository.countEmitidasEntre(any(), any(), any())).thenReturn(4L);
        when(recetaRepository.countConvertidasEntre(any(), any(), any()))
                .thenReturn(2L);

        var resp = service.estadisticas(6);

        assertThat(resp.meses()).hasSize(6);
        // El último elemento es el mes en curso (orden cronológico ascendente).
        var actual = java.time.YearMonth.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        var ultimo = resp.meses().get(5);
        assertThat(ultimo.year()).isEqualTo(actual.getYear());
        assertThat(ultimo.month()).isEqualTo(actual.getMonthValue());
        assertThat(ultimo.recetasEmitidas()).isEqualTo(4);
        assertThat(ultimo.recetasAplicadas()).isEqualTo(2);
        assertThat(ultimo.comisionTotal()).isEqualByComparingTo("5593.00");
        // El primero es (n-1) meses atrás.
        assertThat(resp.meses().get(0).year() * 12 + resp.meses().get(0).month())
                .isEqualTo(actual.minusMonths(5).getYear() * 12 + actual.minusMonths(5).getMonthValue());
    }

    @Test
    void estadisticas_acotaElRangoDeMeses() {
        when(recetaRepository.countEmitidasEntre(any(), any(), any())).thenReturn(0L);
        when(recetaRepository.countConvertidasEntre(any(), any(), any())).thenReturn(0L);

        assertThat(service.estadisticas(0).meses()).hasSize(6); // <=0 → default 6
        assertThat(service.estadisticas(1).meses()).hasSize(1); // mínimo
        assertThat(service.estadisticas(100).meses()).hasSize(24); // máximo
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
        when(recetaRepository.countConvertidasEntre(any(), any(), any())).thenReturn(1L);
        when(recetaRepository.findConvertidasEntre(eq(nutri.getId()), any(), any())).thenReturn(List.of(r));
        when(pacienteRepository.findById(juan.getId())).thenReturn(Optional.of(juan));

        var resp = service.cierreMensual(2026, 7);

        assertThat(resp.detalle()).hasSize(1);
        var d = resp.detalle().get(0);
        assertThat(d.recetaCodigo()).isEqualTo("RX-ABC123");
        assertThat(d.paciente()).isEqualTo("Juan Pérez");
        assertThat(d.comisionMonto()).isEqualByComparingTo("3150.00");
    }
}
