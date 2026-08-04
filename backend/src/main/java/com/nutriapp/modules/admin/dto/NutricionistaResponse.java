package com.nutriapp.modules.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de la bandeja de validación de nutricionistas (admin). */
public record NutricionistaResponse(
        UUID id,
        String nombre,
        String apellido,
        String email,
        String telefono,
        String matricula,
        // C-08 — datos fiscales del registro.
        String dni,
        String cuit,
        String condicionFiscal,
        /** ¿Subió la matrícula? El listado no arrastra los bytes, sólo si hay algo para mirar. */
        boolean tieneMatricula,
        String estadoValidacion,
        Instant validadoAt,
        String notasValidacion,
        Instant createdAt,
        /** C-01: override propio. null = usa el global (ver {@code descuentoPctEfectivo}). */
        BigDecimal descuentoPct,
        BigDecimal comisionPct,
        /** Lo que realmente se le aplica hoy, ya resuelto contra el global. */
        BigDecimal descuentoPctEfectivo,
        BigDecimal comisionPctEfectiva
) {}
