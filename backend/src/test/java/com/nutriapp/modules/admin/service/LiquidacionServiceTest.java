package com.nutriapp.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.modules.admin.dto.LiquidacionResponse;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** C-05 — liquidación de comisiones (post-demo 2026-07-31). */
@ExtendWith(MockitoExtension.class)
class LiquidacionServiceTest {

    @Mock
    private RecetaRepository recetaRepository;

    @InjectMocks
    private LiquidacionService service;

    private static Receta receta(EstadoReceta estado, String comision) {
        Receta r = new Receta();
        r.setId(UUID.randomUUID());
        r.setCodigo("RX-" + estado.name().charAt(0) + "00001");
        r.setEstado(estado);
        r.setComisionMonto(new BigDecimal(comision));
        r.setOrdenPaidAt(Instant.parse("2026-07-20T15:00:00Z"));
        return r;
    }

    @Test
    void liquida_las_aplicadas_y_suma_la_comision() {
        Receta a = receta(EstadoReceta.APLICADA, "1500.00");
        Receta b = receta(EstadoReceta.APLICADA, "2350.50");
        when(recetaRepository.findByIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of(a, b));

        LiquidacionResponse res = service.liquidar(List.of(a.getId(), b.getId()));

        assertThat(res.liquidadas()).isEqualTo(2);
        assertThat(res.comisionTotal()).isEqualByComparingTo("3850.50");
        assertThat(res.omitidas()).isEmpty();
        assertThat(a.getEstado()).isEqualTo(EstadoReceta.LIQUIDADA);
        assertThat(b.getEstado()).isEqualTo(EstadoReceta.LIQUIDADA);
        assertThat(a.getLiquidadaAt()).isNotNull();
        // Misma corrida = misma marca de tiempo para todo el lote.
        assertThat(a.getLiquidadaAt()).isEqualTo(b.getLiquidadaAt()).isEqualTo(res.liquidadaAt());
    }

    @Test
    void es_idempotente_no_recuenta_una_ya_liquidada() {
        Receta ya = receta(EstadoReceta.LIQUIDADA, "999.00");
        Instant original = Instant.parse("2026-07-01T10:00:00Z");
        ya.setLiquidadaAt(original);
        when(recetaRepository.findByIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of(ya));

        LiquidacionResponse res = service.liquidar(List.of(ya.getId()));

        assertThat(res.liquidadas()).isZero();
        assertThat(res.comisionTotal()).isEqualByComparingTo("0");
        assertThat(res.omitidas()).singleElement()
                .satisfies(o -> assertThat(o.motivo()).isEqualTo("ya estaba liquidada"));
        assertThat(ya.getLiquidadaAt()).isEqualTo(original); // no se pisa la fecha original
        verify(recetaRepository, never()).save(ya);
    }

    @Test
    void no_liquida_una_receta_que_no_convirtio() {
        Receta pendiente = receta(EstadoReceta.PENDIENTE, "0.00");
        when(recetaRepository.findByIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of(pendiente));

        LiquidacionResponse res = service.liquidar(List.of(pendiente.getId()));

        assertThat(res.liquidadas()).isZero();
        assertThat(res.omitidas()).singleElement()
                .satisfies(o -> assertThat(o.motivo()).contains("no convertida").contains("PENDIENTE"));
        assertThat(pendiente.getEstado()).isEqualTo(EstadoReceta.PENDIENTE);
    }

    @Test
    void reporta_las_recetas_inexistentes_en_vez_de_ignorarlas() {
        UUID fantasma = UUID.randomUUID();
        when(recetaRepository.findByIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());

        LiquidacionResponse res = service.liquidar(List.of(fantasma));

        assertThat(res.liquidadas()).isZero();
        assertThat(res.omitidas()).singleElement().satisfies(o -> {
            assertThat(o.recetaId()).isEqualTo(fantasma);
            assertThat(o.motivo()).isEqualTo("no existe");
        });
    }
}
