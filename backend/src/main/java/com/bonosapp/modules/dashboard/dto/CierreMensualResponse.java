package com.bonosapp.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cierre mensual del nutricionista: conversión y comisión del período + detalle de las
 * recetas convertidas (contrato 05-api-endpoints §Dashboard).
 *
 * <p><b>Sin el total facturado</b>: la nutricionista ve lo que gana, no lo que la tienda vendió.
 * Es la misma línea que trazó C-02 con los precios (call 53:35) — el monto de la orden es
 * información comercial de TBC, y mostrarlo invita a que se calcule la comisión por su cuenta
 * sobre un número que además incluye productos que ella no recetó. El admin sí lo ve, en su
 * cierre consolidado, porque es con lo que liquida.
 */
public record CierreMensualResponse(
        int year,
        int month,
        long recetasEmitidas,
        long recetasAplicadas,
        BigDecimal tasaConversion,
        BigDecimal comisionTotal,
        List<Detalle> detalle
) {
    public record Detalle(
            String recetaCodigo,
            String paciente,
            BigDecimal comisionMonto,
            Instant paidAt,
            /** C-05: cuándo se le pagó esta comisión. null = todavía no liquidada. */
            Instant liquidadaAt
    ) {}
}
