package com.nutriapp.modules.dashboard.dto;

import com.nutriapp.modules.receta.dto.RecetaResponse;
import java.math.BigDecimal;
import java.util.List;

/** Primera pantalla post-login del nutricionista. */
public record DashboardResumenResponse(
        long recetasPendientes,
        long recetasAplicadasMes,
        long recetasVencidasMes,
        BigDecimal comisionMesActual,
        BigDecimal ventasGeneradasMesActual,
        List<RecetaResponse> ultimasRecetas
) {}
