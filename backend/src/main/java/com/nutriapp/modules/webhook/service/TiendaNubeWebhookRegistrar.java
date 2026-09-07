package com.nutriapp.modules.webhook.service;

import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.health.IntegrationHealthRegistry;
import com.nutriapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient.Webhook;
import com.nutriapp.modules.notificacion.NotificacionProperties;
import com.nutriapp.modules.webhook.dto.RegistrarWebhooksResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Suscribe la app a los eventos de la tienda. Es un paso de puesta en marcha por tienda —
 * no hay UI en TiendaNube para hacerlo: o se registra por API o no llega ningún webhook.
 *
 * <p>Idempotente: lista lo que ya está y sólo crea lo que falta, así se puede correr de nuevo sin
 * duplicar suscripciones (TiendaNube acepta duplicados y mandaría el evento dos veces).
 *
 * <p>La URL sale de {@code APP_PUBLIC_URL} y <b>tiene que ser HTTPS pública</b>: TiendaNube rechaza
 * cualquier otra cosa, y en dev directamente no llegan (para eso está el polling de respaldo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TiendaNubeWebhookRegistrar {

    /** {@code order/paid} es el que dispara la conversión; el resto no lo procesamos. */
    private static final List<String> EVENTOS = List.of("order/paid");

    private static final String PATH = "/api/v1/webhooks/tiendanube";

    private final TiendaNubeClient tiendaNubeClient;
    private final NotificacionProperties notificacionProperties;
    private final IntegrationHealthRegistry health;

    public RegistrarWebhooksResponse registrar() {
        String url = notificacionProperties.appUrlNormalizada() + PATH;
        if (!url.startsWith("https://")) {
            throw new IllegalStateException(
                    "APP_PUBLIC_URL tiene que ser HTTPS para registrar el webhook (es: " + url + ")");
        }
        try {
            List<Webhook> existentes = tiendaNubeClient.listWebhooks();
            List<String> creados = new ArrayList<>();
            List<String> yaEstaban = new ArrayList<>();
            for (String evento : EVENTOS) {
                boolean registrado = existentes.stream()
                        .anyMatch(w -> evento.equals(w.event()) && url.equals(w.url()));
                if (registrado) {
                    yaEstaban.add(evento);
                    continue;
                }
                tiendaNubeClient.createWebhook(evento, url);
                creados.add(evento);
            }
            health.registrarExito(Proveedor.TIENDANUBE);
            log.info("[tiendanube-webhooks] url={} creados={} ya-estaban={}", url, creados, yaEstaban);
            return new RegistrarWebhooksResponse(url, creados, yaEstaban);
        } catch (IntegrationUnavailableException ex) {
            health.registrarError(Proveedor.TIENDANUBE, ex.getMessage());
            throw ex;
        }
    }
}
