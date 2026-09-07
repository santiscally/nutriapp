package com.bonosapp.modules.receta.service;

import com.bonosapp.modules.receta.dto.ResyncCuponesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reintenta periódicamente el registro de los cupones que quedaron pendientes de sync (2.8).
 * En stub es inofensivo: {@link CuponSyncService#resync()} deja todo PENDIENTE sin consumir nada.
 * Al pasar TiendaNube a live (2.1) drena solo lo acumulado. Idempotente y sin lock multi-instancia
 * (inocuo con 1 instancia; si se escala, agregar ShedLock — mismo TODO que el resto de los jobs).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CuponSyncJob {

    private final CuponSyncService cuponSyncService;

    @Scheduled(
            fixedDelayString = "${bonosapp.cupones.sync-interval-ms}",
            initialDelayString = "${bonosapp.cupones.sync-interval-ms}")
    public void sync() {
        try {
            ResyncCuponesResponse r = cuponSyncService.resync();
            if (r.intentados() > 0) {
                log.debug("[cupon-sync-job] intentados={} sincronizados={} pendientes={}",
                        r.intentados(), r.sincronizados(), r.pendientes());
            }
        } catch (Exception ex) {
            log.error("[cupon-sync-job] error en el resync de cupones", ex);
        }
    }
}
