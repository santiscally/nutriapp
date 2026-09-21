package com.bonosapp.modules.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/** S-13 — serie mensual consolidada, cronológica ascendente; el último elemento es el mes en curso. */
public record AdminEstadisticasResponse(List<MesStat> meses) {

    public record MesStat(
            int year,
            int month,
            long recetasEmitidas,
            long recetasAplicadas,
            BigDecimal comisionTotal,
            BigDecimal facturadoTotal
    ) {}
}
