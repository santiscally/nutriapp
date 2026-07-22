package com.nutriapp.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cierre mensual del nutricionista: conversión y comisión del período + detalle de las
 * recetas convertidas (contrato 05-api-endpoints §Dashboard).
 */
public record CierreMensualResponse(
        int year,
        int month,
        long recetasEmitidas,
        long recetasAplicadas,
        BigDecimal tasaConversion,
        BigDecimal ventasGeneradas,
        BigDecimal comisionTotal,
        List<Detalle> detalle
) {
    public record Detalle(
            String recetaCodigo,
            String paciente,
            BigDecimal ordenTotal,
            BigDecimal comisionMonto,
            Instant paidAt
    ) {}
}
