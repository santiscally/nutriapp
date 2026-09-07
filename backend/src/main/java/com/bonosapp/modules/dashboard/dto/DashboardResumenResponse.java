package com.bonosapp.modules.dashboard.dto;

import com.bonosapp.modules.receta.dto.RecetaResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * Primera pantalla post-login del nutricionista.
 *
 * <p><b>Sin el total facturado</b>: la nutricionista ve lo que gana, no lo que la tienda vendió.
 * Es la misma línea que trazó C-02 con los precios (call 53:35) — el monto de la orden es
 * información comercial de TBC, y mostrarlo invita a que se calcule la comisión por su cuenta
 * sobre un número que además incluye productos que ella no recetó. El admin sí lo ve, en su
 * cierre consolidado, porque es con lo que liquida.
 */
public record DashboardResumenResponse(
        long recetasPendientes,
        long recetasAplicadasMes,
        long recetasVencidasMes,
        BigDecimal comisionMesActual,
        List<RecetaResponse> ultimasRecetas
) {}
