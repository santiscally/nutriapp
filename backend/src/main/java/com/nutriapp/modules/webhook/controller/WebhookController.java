package com.nutriapp.modules.webhook.controller;

import com.nutriapp.modules.webhook.service.TiendaNubeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada de webhooks. Sin auth JWT: la firma HMAC ES la autenticación (ver SecurityConfig,
 * {@code /api/v1/webhooks/**} permitAll). Se recibe el body CRUDO (byte[]) porque el HMAC se
 * calcula sobre los bytes exactos — no sobre el JSON re-serializado.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final TiendaNubeWebhookService service;

    /** Webhook order/paid: verifica HMAC, persiste idempotente y responde 200 (procesamiento async). */
    @PostMapping("/tiendanube")
    public ResponseEntity<Void> tiendanube(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "x-linkedstore-hmac-sha256", required = false) String hmac) {
        service.recibir(body == null ? new byte[0] : body, hmac);
        return ResponseEntity.ok().build();
    }
}
