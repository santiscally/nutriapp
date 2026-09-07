package com.bonosapp.modules.admin.dto;

import java.time.Instant;

/**
 * Estado de una integración externa para el panel de admin (2.7).
 *
 * @param proveedor    "contabilium" | "tiendanube" | "mail" | "whatsapp"
 * @param modo         "stub" | "live"
 * @param disponible   true/false según el último resultado real; null si aún no se interactuó
 *                     (en stub siempre false: no hay conexión por diseño)
 * @param pendientes   trabajo acumulado por caída del proveedor (cupones sin sync / notifs QUEUED)
 * @param ultimoError  mensaje del último error registrado; null si nunca falló
 * @param ultimoErrorAt momento del último error; null si nunca falló
 * @param ultimaSync   momento de la última interacción exitosa (o última sync de catálogo)
 * @param sincronizando true si hay una sincronización en curso ahora (solo contabilium); null si no aplica
 * @param ultimoResultado resumen del último sync ("revisados=.. creados=.." o "error: ..") — solo contabilium; null si no aplica
 */
public record IntegracionEstadoResponse(
        String proveedor,
        String modo,
        Boolean disponible,
        long pendientes,
        String ultimoError,
        Instant ultimoErrorAt,
        Instant ultimaSync,
        Boolean sincronizando,
        String ultimoResultado
) {}
