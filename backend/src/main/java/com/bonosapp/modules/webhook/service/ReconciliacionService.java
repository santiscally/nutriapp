package com.bonosapp.modules.webhook.service;

import com.bonosapp.integrations.tiendanube.TiendaNubeClient;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.webhook.dto.ReconciliacionResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Barrido profundo de órdenes pagadas, para los bonos que se compraron y quedaron PENDIENTES.
 *
 * <p><b>Por qué existe:</b> el polling de respaldo mira sólo las últimas 24 h. Si un webhook no
 * llega —y no llegar es silencioso: no hay error, no hay log, no hay fila en {@code webhook_events}—
 * la orden sale de esa ventana al día siguiente y <b>el bono no se aplica nunca más</b>. Eso ya pasó
 * en producción con un bono comprado un viernes que seguía PENDIENTE el martes. El costo no es
 * cosmético: es la comisión de una profesional que no se liquida.
 *
 * <p>Corre solo una vez por día sobre la vigencia completa del bono, y además se puede disparar a
 * mano desde el panel del admin cuando alguien reporta uno colgado.
 *
 * <p>Idempotente: {@link TiendaNubeWebhookService#aplicarOrden} saltea lo ya aplicado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliacionService {

    /** Tope de la ventana manual: más atrás que esto no hay bono que pueda seguir vigente. */
    private static final int MAX_HORAS = 24 * 90;

    private final TiendaNubeClient tiendaNubeClient;
    private final TiendaNubeWebhookService webhookService;
    private final RecetaRepository recetaRepository;

    /**
     * Barrido nocturno sobre 30 días, la vigencia de un bono. A las 4 AM para no cruzarse con el
     * job de vencimiento de las 3.
     */
    @Scheduled(cron = "${bonosapp.webhooks.reconciliacion-cron:0 0 4 * * *}",
            zone = "America/Argentina/Buenos_Aires")
    public void barridoDiario() {
        ReconciliacionResponse r = reconciliar(24 * 30);
        if (r.bonosAplicados() > 0) {
            // WARN y no INFO: que el barrido encuentre algo significa que un webhook se perdió.
            log.warn("[reconciliacion] el barrido diario aplicó {} bono(s) que el webhook no trajo: {}",
                    r.bonosAplicados(), r.codigos());
        }
    }

    /**
     * Revisa las órdenes pagadas de las últimas {@code horas} y aplica las que matcheen un bono
     * PENDIENTE. Devuelve qué encontró, para que el admin vea si sirvió de algo.
     */
    public ReconciliacionResponse reconciliar(int horas) {
        int ventana = Math.max(1, Math.min(MAX_HORAS, horas));
        Instant desde = Instant.now().minus(ventana, ChronoUnit.HOURS);

        Set<String> pendientesAntes = codigosPendientes();
        List<TiendaNubeClient.Order> ordenes = tiendaNubeClient.getPaidOrdersSince(desde);
        int aplicados = 0;
        for (TiendaNubeClient.Order o : ordenes) {
            aplicados += webhookService.aplicarOrden(o);
        }

        // Los que dejaron de estar pendientes son los que movió esta corrida.
        List<String> codigos = new ArrayList<>(pendientesAntes);
        codigos.removeAll(codigosPendientes());
        codigos.sort(String::compareTo);

        log.info("[reconciliacion] ventana={}h ordenes={} aplicados={} codigos={}",
                ventana, ordenes.size(), codigos.size(), codigos);
        return new ReconciliacionResponse(ventana, desde, ordenes.size(), codigos.size(), codigos,
                mensaje(ordenes.size(), codigos));
    }

    private Set<String> codigosPendientes() {
        return recetaRepository.findByEstadoAndDeletedAtIsNull(EstadoReceta.PENDIENTE).stream()
                .map(Receta::getCodigo)
                .collect(Collectors.toSet());
    }

    private static String mensaje(int ordenes, List<String> codigos) {
        if (ordenes == 0) {
            return "No hay órdenes pagadas en esa ventana.";
        }
        if (codigos.isEmpty()) {
            return "Se revisaron " + ordenes + " orden(es) pagada(s); ningún bono pendiente matcheó.";
        }
        return "Se aplicaron " + codigos.size() + " bono(s) que estaban pendientes: "
                + String.join(", ", codigos) + ".";
    }
}
