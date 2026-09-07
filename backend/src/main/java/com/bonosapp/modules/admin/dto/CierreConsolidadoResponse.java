package com.bonosapp.modules.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * C-06 — cierre consolidado del admin: cuánto comisionó cada nutricionista en un rango, para
 * poder liquidarle (call 55:28). Sin paginar a propósito: es una fila por nutricionista con
 * actividad, y el exportable tiene que salir completo, no la página que se esté mirando.
 *
 * <p>Incluye el CUIT que Gon pidió para el exportable (57:02); lo cargan en el registro (C-08),
 * así que puede venir vacío en las nutricionistas dadas de alta antes de ese cambio.
 */
public record CierreConsolidadoResponse(
        LocalDate desde,
        LocalDate hasta,
        List<Fila> filas,
        Totales totales
) {
    public record Fila(
            UUID nutricionistaId,
            String nombre,
            String apellido,
            String email,
            /** C-08: para liquidarle la factura. Puede ser null en altas viejas. */
            String cuit,
            /** Convertidas en la ventana (incluye las ya liquidadas). */
            long recetas,
            /** Suma de lo realmente pagado en TiendaNube (C-03). */
            BigDecimal facturado,
            BigDecimal comision,
            /** Subconjunto todavía impago: es lo que se liquida. */
            long recetasPendientes,
            BigDecimal comisionPendiente,
            /** Ids de las impagas, para mandarlas a POST /admin/liquidaciones de un click. */
            List<UUID> recetaIdsPendientes
    ) {}

    public record Totales(
            long recetas,
            BigDecimal facturado,
            BigDecimal comision,
            BigDecimal comisionPendiente
    ) {}
}
