package com.bonosapp.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.modules.admin.dto.AdminDashboardResumenResponse;
import com.bonosapp.modules.admin.dto.AdminEstadisticasResponse;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.receta.service.RecetaService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** S-13 — el panel del admin es el de la profesional sin filtro de profesional, más la plata. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardServiceTest {

    @Mock RecetaRepository recetaRepository;
    @Mock RecetaService recetaService;
    @Mock NutricionistaRepository nutricionistaRepository;

    private AdminDashboardService service() {
        return new AdminDashboardService(recetaRepository, recetaService, nutricionistaRepository,
                new ProfesionalesLookup(nutricionistaRepository));
    }

    @Test
    void consolidaSobreTodasLasProfesionales() {
        when(recetaRepository.findTop8ByDeletedAtIsNullOrderByEmitidaAtDesc()).thenReturn(List.of());
        when(recetaRepository.countPorEstado(isNull(), any())).thenReturn(12L);
        when(recetaRepository.sumFacturadoEntre(isNull(), any(), any()))
                .thenReturn(new BigDecimal("980000.00"));
        when(recetaRepository.sumComisionEntre(isNull(), any(), any()))
                .thenReturn(new BigDecimal("41000.00"));
        when(nutricionistaRepository.countByActivoTrueAndDeletedAtIsNull()).thenReturn(14L);
        when(nutricionistaRepository.countByEstadoValidacionAndDeletedAtIsNull(EstadoValidacion.PENDIENTE))
                .thenReturn(3L);

        AdminDashboardResumenResponse r = service().resumen();

        assertThat(r.recetasPendientes()).isEqualTo(12L);
        assertThat(r.facturadoMesActual()).isEqualByComparingTo("980000.00");
        assertThat(r.comisionMesActual()).isEqualByComparingTo("41000.00");
        assertThat(r.profesionalesActivos()).isEqualTo(14L);
        assertThat(r.profesionalesPendientes()).isEqualTo(3L);
        // El null es la clave: sin filtro de profesional, las métricas son de todas.
        verify(recetaRepository).countPorEstado(isNull(), any(EstadoReceta.class));
        verify(recetaRepository).sumFacturadoEntre(isNull(), any(Instant.class), any(Instant.class));
    }

    @Test
    void laSerieSeAcotaA24MesesYVaEnOrdenCronologico() {
        AdminEstadisticasResponse r = service().estadisticas(99);

        assertThat(r.meses()).hasSize(24);
        AdminEstadisticasResponse.MesStat primero = r.meses().get(0);
        AdminEstadisticasResponse.MesStat ultimo = r.meses().get(23);
        assertThat(primero.year() * 12 + primero.month()).isLessThan(ultimo.year() * 12 + ultimo.month());
    }

    @Test
    void mesesInvalidoCaeAlDefaultDeSeis() {
        assertThat(service().estadisticas(0).meses()).hasSize(6);
    }
}
