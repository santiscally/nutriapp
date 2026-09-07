package com.bonosapp.modules.receta.dto;

import com.bonosapp.modules.notificacion.dto.NotificacionResponse;
import com.bonosapp.modules.paciente.dto.PacienteResponse;
import com.bonosapp.modules.producto.dto.ProductoResponse;
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
        /**
         * Link {@code wa.me} para que la nutricionista mande la receta por su propio WhatsApp
         * (2.4). null cuando no corresponde: receta que ya no es PENDIENTE o paciente sin
         * teléfono utilizable. El envío por WhatsApp es manual — el canal automático es el email.
         */
        String waMeUrl,
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

    /**
     * Datos de la compra que convirtió la receta.
     *
     * <p><b>Sin el total facturado</b>: la nutricionista ve lo que gana, no lo que la tienda
     * vendió. Es la misma línea que trazó C-02 con los precios (call 53:35) — el monto de la orden
     * es información comercial de TBC, y mostrarlo invita a que se calcule la comisión por su
     * cuenta sobre un número que además incluye productos que ella no recetó. El admin sí lo ve,
     * en su cierre consolidado, porque es con lo que liquida.
     */
    public record Conversion(
            Integer ordenNumero,
            Instant paidAt,
            BigDecimal comisionPct,
            BigDecimal comisionMonto,
            /** C-05: cuándo el admin pagó esta comisión. null = convertida pero todavía impaga. */
            Instant liquidadaAt
    ) {}
}
