package com.nutriapp.modules.receta.dto;

import com.nutriapp.modules.notificacion.dto.NotificacionResponse;
import com.nutriapp.modules.paciente.dto.PacienteResponse;
import com.nutriapp.modules.producto.dto.ProductoResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecetaResponse(
        UUID id,
        String codigo,
        String estado,
        PacienteResponse paciente,
        List<Item> items,
        BigDecimal descuentoPct,
        Instant emitidaAt,
        LocalDate venceAt,
        String cuponSyncEstado,
        List<NotificacionResponse> notificaciones,
        Conversion conversion
) {
    public record Item(
            ProductoResponse producto,
            int cantidad,
            BigDecimal precioLista,
            String indicaciones
    ) {}

    public record Conversion(
            Integer ordenNumero,
            BigDecimal ordenTotal,
            Instant paidAt,
            BigDecimal comisionPct,
            BigDecimal comisionMonto
    ) {}
}
