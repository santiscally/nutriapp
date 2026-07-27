package com.nutriapp.modules.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recepción y procesamiento de webhooks de TiendaNube (evento {@code order/paid}).
 *
 * <p>Diseño en dos tiempos, como el dispatcher de notificaciones:
 * <ol>
 *   <li><b>{@link #recibir}</b> (sincrónico, en el request): verifica HMAC, persiste el evento
 *       de forma idempotente y responde. NO hace I/O externo.</li>
 *   <li><b>{@link #procesarEvento}</b> (async, vía WebhookProcessor): lee la orden en TiendaNube
 *       y aplica la conversión. El fetch de la orden ocurre FUERA de transacción; si la integración
 *       está en stub/caída, el evento queda sin procesar y se reintenta (no consume nada).</li>
 * </ol>
 * {@link #aplicarOrden} es el núcleo de matcheo cupón→receta→APLICADA, reusado por el webhook,
 * el polling de respaldo y el simulador de dev.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TiendaNubeWebhookService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final WebhookEventRepository eventRepo;
    private final RecetaRepository recetaRepository;
    private final TiendaNubeClient tiendaNubeClient;
    private final HmacVerifier hmacVerifier;
    private final IntegrationsProperties integrationsProps;
    private final ConfiguracionService configuracionService;
    private final ObjectMapper objectMapper;

    /**
     * Verifica la firma, persiste el evento (idempotente por origen+evento+recurso) y vuelve.
     * @throws WebhookSignatureException si la firma HMAC es inválida/ausente (→ 401).
     */
    @Transactional
    public void recibir(byte[] rawBody, String hmacHeader) {
        String secret = integrationsProps.tiendanube().webhookSecret();
        if (!hmacVerifier.verify(secret, rawBody, hmacHeader)) {
            throw new WebhookSignatureException("Firma HMAC del webhook inválida o ausente");
        }

        JsonNode payload = parse(rawBody);
        if (payload == null) {
            log.warn("[webhook] body firmado pero no es JSON válido, ignorado");
            return;
        }
        String evento = payload.path("event").asText(null);
        Long recursoId = payload.hasNonNull("id") ? payload.get("id").asLong() : null;
        if (evento == null || evento.isBlank() || recursoId == null) {
            // Body firmado pero sin los campos que esperamos: lo ignoramos (200) sin persistir.
            log.warn("[webhook] payload sin event/id utilizable, ignorado: {}", payload);
            return;
        }

        if (eventRepo.existsByOrigenAndEventoAndRecursoId(OrigenWebhook.TIENDANUBE, evento, recursoId)) {
            log.info("[webhook] evento repetido {}#{} — ignorado (idempotencia)", evento, recursoId);
            return;
        }

        WebhookEvent ev = new WebhookEvent();
        ev.setOrigen(OrigenWebhook.TIENDANUBE);
        ev.setEvento(evento);
        ev.setRecursoId(recursoId);
        ev.setPayload(new String(rawBody, StandardCharsets.UTF_8));
        try {
            eventRepo.saveAndFlush(ev);
            log.info("[webhook] evento {}#{} persistido, pendiente de procesar", evento, recursoId);
        } catch (DataIntegrityViolationException race) {
            // Carrera: el mismo evento llegó en paralelo. Idempotente: no es un error.
            log.info("[webhook] carrera de idempotencia en {}#{}", evento, recursoId);
        }
    }

    /** Lote de eventos sin procesar (datos desprendidos de la sesión) para el processor. */
    @Transactional(readOnly = true)
    public List<EventoPendiente> tomarLote(int batchSize) {
        return eventRepo.findByProcesadoFalseAndDeletedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, batchSize))
                .stream()
                .map(e -> new EventoPendiente(e.getId(), e.getEvento(), e.getRecursoId()))
                .toList();
    }

    /**
     * Procesa un evento: lee la orden en TiendaNube (I/O, fuera de tx) y aplica la conversión.
     * <ul>
     *   <li>Evento que no es {@code order/paid}: se marca procesado (nada que hacer).</li>
     *   <li>{@link IntegrationUnavailableException}: transitorio, queda sin procesar y se reintenta.</li>
     *   <li>Otro error: se marca procesado con el motivo (evita reintento infinito de un poison).</li>
     * </ul>
     */
    public void procesarEvento(EventoPendiente ev) {
        if (!"order/paid".equals(ev.evento())) {
            marcarProcesado(ev.id(), "evento no manejado: " + ev.evento());
            return;
        }
        try {
            TiendaNubeClient.Order order = tiendaNubeClient.getOrder(ev.recursoId());
            int aplicadas = aplicarOrden(order);
            marcarProcesado(ev.id(), aplicadas == 0 ? "ningún cupón de la orden matcheó una receta pendiente" : null);
        } catch (IntegrationUnavailableException transitorio) {
            log.debug("[webhook] getOrder {} no disponible (stub/caída), se reintentará", ev.recursoId());
        } catch (Exception ex) {
            log.error("[webhook] error procesando evento {} (orden {})", ev.id(), ev.recursoId(), ex);
            marcarProcesado(ev.id(), "error: " + ex.getMessage());
        }
    }

    @Transactional
    public void marcarProcesado(UUID eventId, String error) {
        eventRepo.findById(eventId).ifPresent(ev -> {
            ev.marcarProcesado(error);
            eventRepo.save(ev);
        });
    }

    /**
     * Núcleo de la conversión: para cada cupón de una orden pagada, matchea la receta por código
     * y la pasa a APLICADA con el snapshot de la orden + la comisión. Idempotente: si la receta ya
     * está APLICADA por esta misma orden, no vuelve a tocarla; si está en otro estado, la saltea.
     *
     * @return cuántas recetas quedaron aplicadas por esta orden.
     */
    @Transactional
    public int aplicarOrden(TiendaNubeClient.Order order) {
        if (order == null) {
            return 0;
        }
        if (!order.isPaid()) {
            log.debug("[webhook] orden {} no está pagada (payment_status={}), skip", order.id(), order.paymentStatus());
            return 0;
        }
        if (order.coupons() == null || order.coupons().isEmpty()) {
            log.debug("[webhook] orden {} pagada sin cupones, skip", order.id());
            return 0;
        }

        int aplicadas = 0;
        for (TiendaNubeClient.OrderCoupon cupon : order.coupons()) {
            if (cupon == null || cupon.code() == null || cupon.code().isBlank()) {
                continue;
            }
            Receta receta = recetaRepository.findByCodigoAndDeletedAtIsNull(cupon.code().trim()).orElse(null);
            if (receta == null) {
                continue; // cupón que no es nuestro (o de otra tienda)
            }
            if (receta.getEstado() == EstadoReceta.APLICADA
                    && Objects.equals(receta.getOrdenTiendanubeId(), order.id())) {
                aplicadas++; // ya aplicada por esta misma orden — idempotente
                continue;
            }
            if (receta.getEstado() != EstadoReceta.PENDIENTE) {
                log.info("[webhook] cupón {} de orden {} matchea receta {} pero está {} — no se aplica",
                        cupon.code(), order.id(), receta.getCodigo(), receta.getEstado());
                continue;
            }
            aplicar(receta, order);
            aplicadas++;
        }
        return aplicadas;
    }

    private void aplicar(Receta receta, TiendaNubeClient.Order order) {
        Instant paidAt = order.paidAt() != null ? order.paidAt() : Instant.now();
        BigDecimal total = order.total() != null ? order.total() : BigDecimal.ZERO;
        BigDecimal comisionPct = configuracionService.getComisionPct();
        BigDecimal comisionMonto = total.multiply(comisionPct).divide(CIEN, 2, RoundingMode.HALF_UP);

        receta.setEstado(EstadoReceta.APLICADA);
        receta.setOrdenTiendanubeId(order.id());
        receta.setOrdenNumero(order.number());
        receta.setOrdenTotal(total);
        receta.setOrdenPaidAt(paidAt);
        receta.setAplicadaAt(paidAt);
        receta.setComisionPct(comisionPct);
        receta.setComisionMonto(comisionMonto);
        recetaRepository.save(receta);
        log.info("[webhook] receta {} APLICADA por orden {} (total {}, comisión {}% = {})",
                receta.getCodigo(), order.number(), total, comisionPct, comisionMonto);
    }

    /** Parsea el body; devuelve null si no es JSON válido (el caller lo ignora respondiendo 200). */
    private JsonNode parse(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody == null ? new byte[0] : rawBody);
        } catch (Exception malformed) {
            return null;
        }
    }

    /** Evento a procesar, desprendido de la sesión JPA. */
    public record EventoPendiente(UUID id, String evento, Long recursoId) {}
}
