package com.bonosapp.modules.webhook.service;

import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient;
import com.bonosapp.modules.webhook.WebhookProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polling de respaldo: barre las órdenes pagadas recientes y aplica las que matcheen un cupón
 * nuestro, por si un webhook se perdió (los webhooks de TiendaNube no llegan en dev — sin HTTPS).
 * Idempotente: {@link TiendaNubeWebhookService#aplicarOrden} saltea las recetas ya aplicadas.
 * En stub degrada (integración no conectada) → no-op silencioso.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TiendaNubePollingJob {

    private final TiendaNubeClient tiendaNubeClient;
    private final TiendaNubeWebhookService webhookService;
    private final WebhookProperties props;

    @Scheduled(
            fixedDelayString = "${bonosapp.webhooks.polling-interval-ms}",
            initialDelayString = "${bonosapp.webhooks.polling-interval-ms}")
    public void poll() {
        Instant since = Instant.now().minus(props.pollingWindowHours(), ChronoUnit.HOURS);
        try {
            List<TiendaNubeClient.Order> ordenes = tiendaNubeClient.getPaidOrdersSince(since);
            int aplicadas = 0;
            for (TiendaNubeClient.Order o : ordenes) {
                aplicadas += webhookService.aplicarOrden(o);
            }
            if (!ordenes.isEmpty()) {
                log.info("[tn-polling] {} orden(es) pagada(s) revisada(s), {} receta(s) aplicada(s)",
                        ordenes.size(), aplicadas);
            }
        } catch (IntegrationUnavailableException stub) {
            log.debug("[tn-polling] TiendaNube no conectada (stub), skip");
        } catch (Exception ex) {
            log.error("[tn-polling] error en el polling de respaldo", ex);
        }
    }
}
