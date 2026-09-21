package com.bonosapp.modules.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * S-13 — el panel de la profesional, consolidado sobre todas.
 *
 * <p>Suma lo que el panel de ella no muestra: el <b>facturado</b> y el estado del padrón. El admin
 * sí ve la facturación porque es con lo que liquida (misma línea que el cierre consolidado, C-06).
 */
public record AdminDashboardResumenResponse(
        long recetasPendientes,
        long recetasAplicadasMes,
        long recetasVencidasMes,
        BigDecimal comisionMesActual,
        BigDecimal facturadoMesActual,
        long profesionalesActivos,
        long profesionalesPendientes,
        List<AdminRecetaResponse> ultimasRecetas
) {}
