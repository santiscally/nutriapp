package com.nutriapp.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.IntegrationsProperties;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.modules.configuracion.service.ConfiguracionService;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import com.nutriapp.modules.webhook.entity.OrigenWebhook;
import com.nutriapp.modules.webhook.entity.WebhookEvent;
import com.nutriapp.modules.webhook.exception.WebhookSignatureException;
import com.nutriapp.modules.webhook.repository.WebhookEventRepository;
import com.nutriapp.modules.webhook.service.TiendaNubeWebhookService.EventoPendiente;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TiendaNubeWebhookServiceTest {

    @Mock WebhookEventRepository eventRepo;
    @Mock RecetaRepository recetaRepository;
    @Mock TiendaNubeClient tiendaNubeClient;
    @Mock ConfiguracionService configuracionService;

    private final HmacVerifier hmacVerifier = new HmacVerifier();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SECRET = "test-secret";

    private TiendaNubeWebhookService service;

    @BeforeEach
    void setup() {
        IntegrationsProperties props = new IntegrationsProperties(
                new IntegrationsProperties.Contabilium("stub", null, null, null),
                new IntegrationsProperties.TiendaNube("stub", null, null, null, null, null, null, SECRET),
                new IntegrationsProperties.Mail("stub", null, null),
                new IntegrationsProperties.WhatsApp("stub", null, null, null));
        service = new TiendaNubeWebhookService(
                eventRepo, recetaRepository, tiendaNubeClient, hmacVerifier, props, configuracionService, objectMapper);
        when(recetaRepository.save(any(Receta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(configuracionService.getComisionPct()).thenReturn(new BigDecimal("10"));
    }

    private byte[] body(String event, long id) {
        return ("{\"store_id\":1,\"event\":\"" + event + "\",\"id\":" + id + "}").getBytes(StandardCharsets.UTF_8);
    }

    // --- recibir: HMAC + idempotencia ---

    @Test
    void recibir_firmaInvalida_lanzaYNoPersiste() {
        byte[] b = body("order/paid", 100);

        assertThatThrownBy(() -> service.recibir(b, "deadbeef"))
                .isInstanceOf(WebhookSignatureException.class);

        verify(eventRepo, never()).saveAndFlush(any());
    }

    @Test
    void recibir_valido_persisteEvento() {
        byte[] b = body("order/paid", 123);
        when(eventRepo.existsByOrigenAndEventoAndRecursoId(OrigenWebhook.TIENDANUBE, "order/paid", 123L))
                .thenReturn(false);

        service.recibir(b, hmacVerifier.hex(SECRET, b));

        var captor = org.mockito.ArgumentCaptor.forClass(WebhookEvent.class);
        verify(eventRepo).saveAndFlush(captor.capture());
        WebhookEvent ev = captor.getValue();
        assertThat(ev.getEvento()).isEqualTo("order/paid");
        assertThat(ev.getRecursoId()).isEqualTo(123L);
        assertThat(ev.isProcesado()).isFalse();
    }

    @Test
    void recibir_duplicado_noPersiste() {
        byte[] b = body("order/paid", 123);
        when(eventRepo.existsByOrigenAndEventoAndRecursoId(OrigenWebhook.TIENDANUBE, "order/paid", 123L))
                .thenReturn(true);

        service.recibir(b, hmacVerifier.hex(SECRET, b));

        verify(eventRepo, never()).saveAndFlush(any());
    }

    // --- aplicarOrden: matcheo cupón → APLICADA + comisión ---

    private Receta recetaPendiente(String codigo) {
        Receta r = new Receta();
        r.setId(UUID.randomUUID());
        r.setCodigo(codigo);
        r.setEstado(EstadoReceta.PENDIENTE);
        return r;
    }

    private TiendaNubeClient.Order ordenPagada(long id, String couponCode, String total) {
        return new TiendaNubeClient.Order(id, 306, new BigDecimal(total), "paid", Instant.now(),
                List.of(new TiendaNubeClient.OrderCoupon(9L, couponCode)));
    }

    @Test
    void aplicarOrden_pagadaConMatch_aplicaYCalculaComision() {
        Receta r = recetaPendiente("RX-ABC123");
        when(recetaRepository.findByCodigoAndDeletedAtIsNull("RX-ABC123")).thenReturn(Optional.of(r));

        int aplicadas = service.aplicarOrden(ordenPagada(555L, "RX-ABC123", "1000.00"));

        assertThat(aplicadas).isEqualTo(1);
        assertThat(r.getEstado()).isEqualTo(EstadoReceta.APLICADA);
        assertThat(r.getOrdenTiendanubeId()).isEqualTo(555L);
        assertThat(r.getOrdenTotal()).isEqualByComparingTo("1000.00");
        assertThat(r.getComisionPct()).isEqualByComparingTo("10");
        assertThat(r.getComisionMonto()).isEqualByComparingTo("100.00"); // 10% de 1000
        assertThat(r.getAplicadaAt()).isNotNull();
        verify(recetaRepository).save(r);
    }

    @Test
    void aplicarOrden_noPagada_noAplica() {
        TiendaNubeClient.Order pendiente = new TiendaNubeClient.Order(
                1L, 1, new BigDecimal("500"), "pending", Instant.now(),
                List.of(new TiendaNubeClient.OrderCoupon(1L, "RX-ABC123")));

        assertThat(service.aplicarOrden(pendiente)).isZero();
        verify(recetaRepository, never()).save(any());
    }

    @Test
    void aplicarOrden_recetaNoPendiente_saltea() {
        Receta r = recetaPendiente("RX-VENC01");
        r.setEstado(EstadoReceta.VENCIDA);
        when(recetaRepository.findByCodigoAndDeletedAtIsNull("RX-VENC01")).thenReturn(Optional.of(r));

        assertThat(service.aplicarOrden(ordenPagada(7L, "RX-VENC01", "300"))).isZero();
        assertThat(r.getEstado()).isEqualTo(EstadoReceta.VENCIDA);
        verify(recetaRepository, never()).save(any());
    }

    @Test
    void aplicarOrden_yaAplicadaPorLaMismaOrden_esIdempotente() {
        Receta r = recetaPendiente("RX-DUP001");
        r.setEstado(EstadoReceta.APLICADA);
        r.setOrdenTiendanubeId(555L);
        when(recetaRepository.findByCodigoAndDeletedAtIsNull("RX-DUP001")).thenReturn(Optional.of(r));

        int aplicadas = service.aplicarOrden(ordenPagada(555L, "RX-DUP001", "1000"));

        assertThat(aplicadas).isEqualTo(1); // cuenta como aplicada
        verify(recetaRepository, never()).save(any()); // pero no la re-escribe
    }

    @Test
    void aplicarOrden_cuponSinReceta_devuelveCero() {
        when(recetaRepository.findByCodigoAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        assertThat(service.aplicarOrden(ordenPagada(9L, "RX-NOEXISTE", "800"))).isZero();
        verify(recetaRepository, never()).save(any());
    }

    // --- procesarEvento: transitorio vs no manejado ---

    @Test
    void procesarEvento_integracionCaida_noMarcaProcesado() {
        UUID id = UUID.randomUUID();
        when(tiendaNubeClient.getOrder(999L)).thenThrow(new IntegrationUnavailableException("tiendanube"));

        service.procesarEvento(new EventoPendiente(id, "order/paid", 999L));

        verify(tiendaNubeClient).getOrder(999L);
        verify(eventRepo, never()).findById(any()); // no se marca: queda para reintento
    }

    @Test
    void procesarEvento_eventoNoManejado_marcaProcesadoSinLeerOrden() {
        UUID id = UUID.randomUUID();
        when(eventRepo.findById(id)).thenReturn(Optional.of(new WebhookEvent()));

        service.procesarEvento(new EventoPendiente(id, "order/created", 5L));

        verify(tiendaNubeClient, never()).getOrder(anyLong());
        verify(eventRepo).findById(id); // marcado como procesado (nada que hacer)
    }
}
