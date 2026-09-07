package com.bonosapp.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bonosapp.common.error.ConflictException;
import com.bonosapp.modules.admin.dto.CierreConsolidadoResponse;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** C-06 — cierre consolidado del admin (post-demo 2026-07-31). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CierreConsolidadoServiceTest {

    @Mock RecetaRepository recetaRepository;
    @Mock NutricionistaRepository nutricionistaRepository;

    @InjectMocks CierreConsolidadoService service;

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BETO = UUID.randomUUID();
    private static final LocalDate D1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate D31 = LocalDate.of(2026, 8, 31);

    private static Nutricionista nutri(UUID id, String nombre, String apellido) {
        Nutricionista n = new Nutricionista();
        n.setId(id);
        n.setNombre(nombre);
        n.setApellido(apellido);
        n.setEmail(nombre.toLowerCase() + "@test.dev");
        return n;
    }

    private static Receta receta(UUID nutriId, EstadoReceta estado, String total, String comision) {
        Receta r = new Receta();
        r.setId(UUID.randomUUID());
        r.setNutricionistaId(nutriId);
        r.setEstado(estado);
        r.setOrdenTotal(new BigDecimal(total));
        r.setComisionMonto(new BigDecimal(comision));
        r.setOrdenPaidAt(Instant.parse("2026-08-10T12:00:00Z"));
        return r;
    }

    @Test
    void agrupa_por_nutricionista_y_separa_lo_pendiente_de_lo_ya_liquidado() {
        Receta impaga = receta(ANA, EstadoReceta.APLICADA, "10000", "1000");
        Receta pagada = receta(ANA, EstadoReceta.LIQUIDADA, "5000", "500");
        when(recetaRepository.findConvertidasEntreTodas(any(), any()))
                .thenReturn(List.of(impaga, pagada));
        when(nutricionistaRepository.findAllById(any())).thenReturn(List.of(nutri(ANA, "Ana", "Lopez")));

        CierreConsolidadoResponse res = service.consolidado(D1, D31);

        assertThat(res.filas()).hasSize(1);
        CierreConsolidadoResponse.Fila f = res.filas().get(0);
        assertThat(f.nombre()).isEqualTo("Ana");
        // Las dos convirtieron y cuentan como facturado/comisión...
        assertThat(f.recetas()).isEqualTo(2);
        assertThat(f.facturado()).isEqualByComparingTo("15000");
        assertThat(f.comision()).isEqualByComparingTo("1500");
        // ...pero sólo la APLICADA está pendiente de pago.
        assertThat(f.recetasPendientes()).isEqualTo(1);
        assertThat(f.comisionPendiente()).isEqualByComparingTo("1000");
        assertThat(f.recetaIdsPendientes()).containsExactly(impaga.getId());
    }

    @Test
    void ordena_por_comision_pendiente_descendente() {
        when(recetaRepository.findConvertidasEntreTodas(any(), any())).thenReturn(List.of(
                receta(ANA, EstadoReceta.APLICADA, "1000", "100"),
                receta(BETO, EstadoReceta.APLICADA, "9000", "900")));
        when(nutricionistaRepository.findAllById(any()))
                .thenReturn(List.of(nutri(ANA, "Ana", "Lopez"), nutri(BETO, "Beto", "Diaz")));

        CierreConsolidadoResponse res = service.consolidado(D1, D31);

        // A quien más hay que pagarle, primero.
        assertThat(res.filas()).extracting(CierreConsolidadoResponse.Fila::nombre)
                .containsExactly("Beto", "Ana");
    }

    @Test
    void totaliza_todas_las_filas() {
        when(recetaRepository.findConvertidasEntreTodas(any(), any())).thenReturn(List.of(
                receta(ANA, EstadoReceta.APLICADA, "1000", "100"),
                receta(BETO, EstadoReceta.LIQUIDADA, "9000", "900")));
        when(nutricionistaRepository.findAllById(any()))
                .thenReturn(List.of(nutri(ANA, "Ana", "Lopez"), nutri(BETO, "Beto", "Diaz")));

        CierreConsolidadoResponse.Totales t = service.consolidado(D1, D31).totales();

        assertThat(t.recetas()).isEqualTo(2);
        assertThat(t.facturado()).isEqualByComparingTo("10000");
        assertThat(t.comision()).isEqualByComparingTo("1000");
        assertThat(t.comisionPendiente()).isEqualByComparingTo("100"); // sólo la de Ana
    }

    @Test
    void sin_actividad_devuelve_vacio_con_totales_en_cero() {
        when(recetaRepository.findConvertidasEntreTodas(any(), any())).thenReturn(List.of());

        CierreConsolidadoResponse res = service.consolidado(D1, D31);

        assertThat(res.filas()).isEmpty();
        assertThat(res.totales().recetas()).isZero();
        assertThat(res.totales().comisionPendiente()).isEqualByComparingTo("0");
    }

    @Test
    void rechaza_rangos_invalidos() {
        assertThatThrownBy(() -> service.consolidado(D31, D1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("anterior");
        assertThatThrownBy(() -> service.consolidado(D1, D1.plusYears(3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("rango");
        assertThatThrownBy(() -> service.consolidado(null, D31))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void una_receta_sin_montos_no_rompe_la_suma() {
        Receta rota = receta(ANA, EstadoReceta.APLICADA, "0", "0");
        rota.setOrdenTotal(null);
        rota.setComisionMonto(null);
        when(recetaRepository.findConvertidasEntreTodas(any(), any())).thenReturn(List.of(rota));
        when(nutricionistaRepository.findAllById(any())).thenReturn(List.of(nutri(ANA, "Ana", "Lopez")));

        CierreConsolidadoResponse.Fila f = service.consolidado(D1, D31).filas().get(0);

        assertThat(f.facturado()).isEqualByComparingTo("0");
        assertThat(f.comision()).isEqualByComparingTo("0");
    }
}
