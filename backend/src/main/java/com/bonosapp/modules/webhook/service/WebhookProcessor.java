package com.bonosapp.modules.webhook.service;

import com.bonosapp.modules.webhook.WebhookProperties;
import com.bonosapp.modules.webhook.service.TiendaNubeWebhookService.EventoPendiente;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drena la cola de {@code webhook_events} sin procesar. El fetch de la orden (I/O de red) ocurre
 * dentro de {@link TiendaNubeWebhookService#procesarEvento} FUERA de transacción; acá sólo se
 * itera el lote. En stub, cada evento queda sin procesar y se reintenta en la próxima corrida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookProcessor {

    private final TiendaNubeWebhookService service;
    private final WebhookProperties props;

    @Scheduled(
            fixedDelayString = "${bonosapp.webhooks.process-interval-ms}",
            initialDelayString = "${bonosapp.webhooks.process-interval-ms}")
    public void procesar() {
        List<EventoPendiente> lote = service.tomarLote(props.batchSize());
        if (lote.isEmpty()) {
            return;
        }
        log.debug("[webhook-processor] procesando lote de {} evento(s)", lote.size());
        for (EventoPendiente ev : lote) {
            service.procesarEvento(ev);
        }
    }
}
