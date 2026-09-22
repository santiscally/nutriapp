package com.bonosapp.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.tiendanube.TiendaNubeClient;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.webhook.dto.ReconciliacionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * S-06 — el barrido que rescata los bonos comprados cuyo webhook nunca llegó. Pasó en producción:
 * un bono comprado un viernes seguía PENDIENTE el martes, fuera de la ventana de 24 h del polling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliacionServiceTest {

    @Mock TiendaNubeClient tiendaNubeClient;
    @Mock TiendaNubeWebhookService webhookService;
    @Mock RecetaRepository recetaRepository;

    @InjectMocks ReconciliacionService service;

    private static Receta pendiente(String codigo) {
        Receta r = new Receta();
        r.setCodigo(codigo);
        r.setEstado(EstadoReceta.PENDIENTE);
        return r;
    }

    private static TiendaNubeClient.Order orden(long id, String cupon) {
        return new TiendaNubeClient.Order(id, 306, new BigDecimal("1000.00"), "paid", Instant.now(),
                List.of(new TiendaNubeClient.OrderCoupon(1L, cupon)));
    }

    @Test
    void informaLosBonosQueDejaronDeEstarPendientes() {
        when(tiendaNubeClient.getPaidOrdersSince(any())).thenReturn(List.of(orden(1L, "RX-R7H85N")));
        when(recetaRepository.findByEstadoAndDeletedAtIsNull(EstadoReceta.PENDIENTE))
                .thenReturn(List.of(pendiente("RX-R7H85N"), pendiente("RX-OTRO01")))
                .thenReturn(List.of(pendiente("RX-OTRO01")));

        ReconciliacionResponse r = service.reconciliar(720);

        assertThat(r.bonosAplicados()).isEqualTo(1);
        assertThat(r.codigos()).containsExactly("RX-R7H85N");
        assertThat(r.mensaje()).contains("RX-R7H85N");
        verify(webhookService).aplicarOrden(any(TiendaNubeClient.Order.class));
    }

    /** La ventana de 24 h del polling es justamente la que dejó el bono colgado: acá se pide más. */
    @Test
    void laVentanaLlegaMasAtrasQueElPolling() {
        when(tiendaNubeClient.getPaidOrdersSince(any())).thenReturn(List.of());
        when(recetaRepository.findByEstadoAndDeletedAtIsNull(any())).thenReturn(List.of());

        service.reconciliar(720);

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        verify(tiendaNubeClient).getPaidOrdersSince(desde.capture());
        assertThat(desde.getValue()).isBefore(Instant.now().minus(25, ChronoUnit.HOURS));
    }

    /** Pedir 10 años no puede barrer 10 años: se acota a la vigencia máxima de un bono. */
    @Test
    void laVentanaSeAcota() {
        when(tiendaNubeClient.getPaidOrdersSince(any())).thenReturn(List.of());
        when(recetaRepository.findByEstadoAndDeletedAtIsNull(any())).thenReturn(List.of());

        ReconciliacionResponse r = service.reconciliar(24 * 3650);

        assertThat(r.horas()).isEqualTo(24 * 90);
    }

    @Test
    void sinOrdenesPagadas_loDiceEnElMensaje() {
        when(tiendaNubeClient.getPaidOrdersSince(any())).thenReturn(List.of());
        when(recetaRepository.findByEstadoAndDeletedAtIsNull(any())).thenReturn(List.of());

        assertThat(service.reconciliar(24).mensaje()).contains("No hay órdenes pagadas");
    }
}
