package com.nutriapp.modules.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serie mensual para los gráficos del dashboard (últimos N meses, orden cronológico ascendente —
 * el último elemento es el mes en curso). Todo dato real del nutricionista: no hay proyecciones ni
 * objetivos inventados; el front deriva tendencias/ticket promedio de esta serie.
 */
public record EstadisticasResponse(List<MesStat> meses) {

    public record MesStat(
            int year,
            int month,
            long recetasEmitidas,
            long recetasAplicadas,
            BigDecimal ventasGeneradas,
            BigDecimal comisionTotal
    ) {}
}
