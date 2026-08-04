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
        /** Mensaje humano de degradación del cupón (2.7). null cuando sincronizó bien. */
        String cuponSyncMensaje,
        List<NotificacionResponse> notificaciones,
        Conversion conversion
) {
    /**
     * C-02 (call 53:35): la receta emitida <b>no expone precios</b> — sólo producto y cantidad.
     * El precio de lista se sigue guardando en {@code receta_items} (auditoría), pero no sale por
     * la API: lo que el paciente termina pagando en TiendaNube es otro número, y no queremos que
     * la nutricionista calcule su comisión con un valor que va a ser falso.
     */
    public record Item(
            ProductoResponse producto,
            int cantidad,
            String indicaciones
    ) {}

    public record Conversion(
            Integer ordenNumero,
            BigDecimal ordenTotal,
            Instant paidAt,
            BigDecimal comisionPct,
            BigDecimal comisionMonto,
            /** C-05: cuándo el admin pagó esta comisión. null = convertida pero todavía impaga. */
            Instant liquidadaAt
    ) {}
}
