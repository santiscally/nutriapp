package com.bonosapp.modules.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serie mensual para los gráficos del dashboard (últimos N meses, orden cronológico ascendente —
 * el último elemento es el mes en curso). Todo dato real del nutricionista: no hay proyecciones ni
 * objetivos inventados; el front deriva tendencias de esta serie.
 *
 * <p><b>Sin el total facturado</b>: la nutricionista ve lo que gana, no lo que la tienda vendió.
 * Es la misma línea que trazó C-02 con los precios (call 53:35) — el monto de la orden es
 * información comercial de TBC, y mostrarlo invita a que se calcule la comisión por su cuenta
 * sobre un número que además incluye productos que ella no recetó. El admin sí lo ve, en su
 * cierre consolidado, porque es con lo que liquida.
 */
public record EstadisticasResponse(List<MesStat> meses) {

    public record MesStat(
            int year,
            int month,
            long recetasEmitidas,
            long recetasAplicadas,
            BigDecimal comisionTotal
    ) {}
}
