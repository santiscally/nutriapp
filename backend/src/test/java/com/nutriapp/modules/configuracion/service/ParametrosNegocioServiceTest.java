package com.nutriapp.modules.configuracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** C-01 — % por nutricionista con fallback al global (post-demo 2026-07-31). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParametrosNegocioServiceTest {

    @Mock ConfiguracionService configuracionService;
    @Mock NutricionistaRepository nutricionistaRepository;

    @InjectMocks ParametrosNegocioService service;

    private static Nutricionista nutri(String descuento, String comision) {
        Nutricionista n = new Nutricionista();
        n.setId(UUID.randomUUID());
        n.setEmail("n@test.dev");
        n.setDescuentoPct(descuento == null ? null : new BigDecimal(descuento));
        n.setComisionPct(comision == null ? null : new BigDecimal(comision));
        return n;
    }

    @Test
    void sin_override_usa_los_valores_globales() {
        when(configuracionService.getDescuentoPct()).thenReturn(new BigDecimal("15"));
        when(configuracionService.getComisionPct()).thenReturn(new BigDecimal("10"));
        Nutricionista n = nutri(null, null);

        assertThat(service.descuentoPctDe(n)).isEqualByComparingTo("15");
        assertThat(service.comisionPctDe(n)).isEqualByComparingTo("10");
    }

    @Test
    void con_override_gana_el_de_la_nutricionista() {
        when(configuracionService.getDescuentoPct()).thenReturn(new BigDecimal("15"));
        when(configuracionService.getComisionPct()).thenReturn(new BigDecimal("10"));
        Nutricionista n = nutri("25", "12.5");

        assertThat(service.descuentoPctDe(n)).isEqualByComparingTo("25");
        assertThat(service.comisionPctDe(n)).isEqualByComparingTo("12.5");
        verify(configuracionService, never()).getDescuentoPct();
    }

    @Test
    void override_parcial_solo_pisa_ese_campo() {
        when(configuracionService.getComisionPct()).thenReturn(new BigDecimal("10"));
        Nutricionista n = nutri(null, "20");

        // Comisión propia, descuento global: los dos campos son independientes.
        assertThat(service.comisionPctDe(n)).isEqualByComparingTo("20");
        service.descuentoPctDe(n);
        verify(configuracionService).getDescuentoPct();
    }

    @Test
    void cero_es_un_override_valido_y_no_cae_al_global() {
        when(configuracionService.getComisionPct()).thenReturn(new BigDecimal("10"));
        Nutricionista n = nutri(null, "0");

        // 0% es una decisión del admin, no "sin configurar". Sólo null cae al global.
        assertThat(service.comisionPctDe(n)).isEqualByComparingTo("0");
    }

    @Test
    void por_id_resuelve_contra_la_nutricionista_guardada() {
        Nutricionista n = nutri(null, "18");
        when(nutricionistaRepository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThat(service.comisionPctDe(n.getId())).isEqualByComparingTo("18");
    }

    @Test
    void por_id_inexistente_cae_al_global_en_vez_de_romper() {
        when(configuracionService.getComisionPct()).thenReturn(new BigDecimal("10"));
        UUID fantasma = UUID.randomUUID();
        when(nutricionistaRepository.findById(fantasma)).thenReturn(Optional.empty());

        // Perder una comisión por un dato faltante sería peor que usar el default.
        assertThat(service.comisionPctDe(fantasma)).isEqualByComparingTo("10");
    }
}
