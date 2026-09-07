package com.bonosapp.modules.receta.service;

import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasa a VENCIDA toda receta PENDIENTE cuya vigencia (30 días) ya expiró.
 * Corre a diario Y al arrancar (catch-up): si el proceso estuvo caído en el horario
 * del cron, el startup recupera las que quedaron colgadas — lección imedba
 * (no confiar sólo en el disparo periódico).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecetaVencimientoJob {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final RecetaRepository repo;

    /** Diario a las 03:00 AR. */
    @Scheduled(cron = "${bonosapp.recetas.vencimiento-cron:0 0 3 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void ejecutarProgramado() {
        int n = vencerPendientesExpiradas();
        if (n > 0) {
            log.info("[vencimiento] {} receta(s) pasaron a VENCIDA (cron)", n);
        }
    }

    /** Catch-up al arrancar: recupera lo que el cron no alcanzó a procesar. */
    @EventListener(ApplicationReadyEvent.class)
    public void ejecutarAlArrancar() {
        int n = vencerPendientesExpiradas();
        log.info("[vencimiento] catch-up al startup: {} receta(s) pasaron a VENCIDA", n);
    }

    /** Marca VENCIDA las PENDIENTES con venceAt anterior a hoy. Devuelve cuántas cambió. */
    @Transactional
    public int vencerPendientesExpiradas() {
        LocalDate hoy = LocalDate.now(AR);
        List<Receta> expiradas = repo.findByEstadoAndVenceAtBeforeAndDeletedAtIsNull(EstadoReceta.PENDIENTE, hoy);
        for (Receta r : expiradas) {
            r.setEstado(EstadoReceta.VENCIDA);
        }
        if (!expiradas.isEmpty()) {
            repo.saveAll(expiradas);
        }
        return expiradas.size();
    }
}
