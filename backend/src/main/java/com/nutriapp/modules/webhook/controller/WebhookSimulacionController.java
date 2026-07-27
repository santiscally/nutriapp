package com.nutriapp.modules.webhook.controller;

import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.entity.RecetaItem;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import com.nutriapp.modules.webhook.dto.SimularOrdenRequest;
import com.nutriapp.modules.webhook.service.TiendaNubeWebhookService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulador de conversión — SÓLO perfil dev. En stub no llega ningún webhook real (TiendaNube
 * exige HTTPS y el cupón nunca se creó allá), así que este endpoint fabrica la orden pagada y la
 * pasa por el MISMO {@link TiendaNubeWebhookService#aplicarOrden} que usa el webhook/polling reales.
 * Sirve para la demo con Gon (~fin jul): emitir receta → simular compra → ver APLICADA + $$$.
 * Nunca existe en prod (no está anotado el bean fuera de dev).
 */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/dev/tiendanube")
@RequiredArgsConstructor
public class WebhookSimulacionController {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final TiendaNubeWebhookService webhookService;
    private final RecetaRepository recetaRepository;

    // @Transactional: abre sesión para el cálculo del total (items es lazy y open-in-view=false)
    // y aplicarOrden se une a la misma tx.
    @Transactional
    @PostMapping("/orden-pagada")
    public ResponseEntity<Map<String, Object>> simularOrdenPagada(@Valid @RequestBody SimularOrdenRequest req) {
        Receta receta = recetaRepository.findByCodigoAndDeletedAtIsNull(req.recetaCodigo())
                .orElseThrow(() -> new NotFoundException("Receta no encontrada: " + req.recetaCodigo()));

        BigDecimal total = req.ordenTotal() != null ? req.ordenTotal() : totalConDescuento(receta);
        long ordenId = req.ordenTiendanubeId() != null
                ? req.ordenTiendanubeId()
                : 500_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 500_000L);
        int ordenNumero = req.ordenNumero() != null ? req.ordenNumero() : (int) Math.floorMod(ordenId, 100_000L);

        TiendaNubeClient.Order order = new TiendaNubeClient.Order(
                ordenId, ordenNumero, total, "paid", Instant.now(),
                List.of(new TiendaNubeClient.OrderCoupon(null, receta.getCodigo())));

        int aplicadas = webhookService.aplicarOrden(order);

        return ResponseEntity.ok(Map.of(
                "recetaCodigo", receta.getCodigo(),
                "ordenTiendanubeId", ordenId,
                "ordenNumero", ordenNumero,
                "ordenTotal", total,
                "recetasAplicadas", aplicadas));
    }

    /** Total simulado = Σ(precioLista·cantidad) con el % de descuento de la receta aplicado. */
    private BigDecimal totalConDescuento(Receta receta) {
        BigDecimal bruto = receta.getItems().stream()
                .map(this::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal factor = BigDecimal.ONE.subtract(receta.getDescuentoPct().divide(CIEN, 4, RoundingMode.HALF_UP));
        return bruto.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal subtotal(RecetaItem item) {
        return item.getPrecioLista().multiply(BigDecimal.valueOf(item.getCantidad()));
    }
}
