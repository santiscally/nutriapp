package com.bonosapp.modules.admin.dto;

import com.bonosapp.modules.paciente.dto.PacienteResponse;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * S-14 — un bono visto por el admin: los mismos campos que ve la profesional más de quién es y
 * cuánto facturó la orden.
 *
 * <p>Deliberadamente plano y con los nombres de {@link RecetaResponse}: el front ya tiene ese tipo
 * y lo reusa tal cual, sin mantener una forma paralela.
 *
 * <p>No incluye {@code waMeUrl} ni las notificaciones: el link de WhatsApp lo manda la profesional
 * desde su propio teléfono, no el admin.
 */
public record AdminRecetaResponse(
        UUID id,
        String codigo,
        String estado,
        PacienteResponse paciente,
        List<RecetaResponse.Item> items,
        BigDecimal descuentoPct,
        Instant emitidaAt,
        LocalDate venceAt,
        String cuponSyncEstado,
        Profesional nutricionista,
        Conversion conversion
) {
    public record Profesional(UUID id, String nombre, String apellido, String email) {}

    /** La de la profesional más {@code ordenTotal}: el admin sí ve lo que facturó la tienda. */
    public record Conversion(
            Integer ordenNumero,
            Instant paidAt,
            BigDecimal comisionPct,
            BigDecimal comisionMonto,
            BigDecimal ordenTotal,
            Instant liquidadaAt
    ) {}

    /** Se arma sobre el RecetaResponse ya construido para no duplicar el armado de items. */
    public static AdminRecetaResponse de(RecetaResponse base, Profesional nutri, BigDecimal ordenTotal) {
        Conversion conversion = base.conversion() == null ? null : new Conversion(
                base.conversion().ordenNumero(),
                base.conversion().paidAt(),
                base.conversion().comisionPct(),
                base.conversion().comisionMonto(),
                ordenTotal,
                base.conversion().liquidadaAt());
        return new AdminRecetaResponse(
                base.id(), base.codigo(), base.estado(), base.paciente(), base.items(),
                base.descuentoPct(), base.emitidaAt(), base.venceAt(), base.cuponSyncEstado(),
                nutri, conversion);
    }
}
