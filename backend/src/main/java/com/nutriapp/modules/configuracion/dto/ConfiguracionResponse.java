package com.nutriapp.modules.configuracion.dto;

import java.math.BigDecimal;

/** Parámetros de negocio vigentes (descuento fijo global + comisión del nutricionista). */
public record ConfiguracionResponse(BigDecimal descuentoPct, BigDecimal comisionPct) {}
