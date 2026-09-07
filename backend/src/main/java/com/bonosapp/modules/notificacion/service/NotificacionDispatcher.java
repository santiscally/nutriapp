package com.bonosapp.modules.notificacion.service;

import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.health.IntegrationHealthRegistry;
import com.bonosapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.bonosapp.integrations.mail.MailSender;
import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.notificacion.service.NotificacionService.NotificacionPendiente;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drena la cola de notificaciones QUEUED contra el port de mail (único canal automático desde
 * que WhatsApp pasó a ser un link manual — ver {@code WaMeLinkBuilder}).
 * <ul>
 *   <li>Éxito → SENT.</li>
 *   <li>{@link IntegrationUnavailableException} (stub/proveedor caído) → sigue QUEUED,
 *       SIN consumir intentos: es transitorio, se reintenta al pasar a live.</li>
 *   <li>Otro error → suma un intento; al superar {@code maxIntentos} pasa a FAILED.</li>
 * </ul>
 * El envío (I/O de red) ocurre FUERA de transacción: el lote se lee en una tx corta y cada
 * resultado se persiste en su propia tx. Así un proveedor lento no retiene una conexión de
 * DB durante los round-trips HTTP (lección GIA: agotamiento del pool Hikari).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionDispatcher {

    private final NotificacionService service;
    private final MailSender mailSender;
    private final NotificacionProperties props;
    private final IntegrationHealthRegistry health;

    @Scheduled(
            fixedDelayString = "${bonosapp.notificaciones.dispatch-interval-ms}",
            initialDelayString = "${bonosapp.notificaciones.dispatch-interval-ms}")
    public void dispatch() {
        List<NotificacionPendiente> lote = service.tomarLote(props.maxIntentos(), props.batchSize());
        if (lote.isEmpty()) {
            return;
        }

        int enviadas = 0, sinConexion = 0, fallidas = 0;
        for (NotificacionPendiente n : lote) {
            try {
                mailSender.send(n.destinatario(), n.asunto(), n.cuerpo());
                service.marcarEnviada(n.id());
                health.registrarExito(Proveedor.MAIL);
                enviadas++;
            } catch (IntegrationUnavailableException ex) {
                service.marcarSinConexion(n.id(), ex.getMessage());
                health.registrarError(Proveedor.MAIL, ex.getMessage());
                sinConexion++;
            } catch (Exception ex) {
                service.marcarFallo(n.id(), ex.getMessage(), props.maxIntentos());
                health.registrarError(Proveedor.MAIL, ex.getMessage());
                fallidas++;
            }
        }
        log.debug("[notif-dispatch] lote={} enviadas={} sin-conexion={} fallidas={}",
                lote.size(), enviadas, sinConexion, fallidas);
    }
}
