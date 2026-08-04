package com.nutriapp.modules.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * % de descuento y de comisión de una nutricionista. Los setea sólo el admin.
 *
 * <p>V011: ambos obligatorios. Antes {@code null} significaba "usá el valor global", pero ese
 * global dejó de existir: hoy no hay a qué caer, así que un campo vacío sería una receta sin
 * descuento o una comisión en cero sin que nadie lo haya decidido.
 */
public record ParametrosNutricionistaRequest(
        @NotNull(message = "El descuento es obligatorio")
        @DecimalMin(value = "0", message = "El descuento no puede ser negativo")
        @DecimalMax(value = "100", message = "El descuento no puede superar 100%")
        BigDecimal descuentoPct,

        @NotNull(message = "La comisión es obligatoria")
        @DecimalMin(value = "0", message = "La comisión no puede ser negativa")
        @DecimalMax(value = "100", message = "La comisión no puede superar 100%")
        BigDecimal comisionPct
) {}
