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
        /**
         * % propios, siempre presentes (V011 eliminó el global y con él la distinción entre el
         * override y el valor "efectivo": lo que está acá es lo que se aplica).
         */
        BigDecimal descuentoPct,
        BigDecimal comisionPct,
        /** ¿Puede loguearse hoy? Refleja el `enabled` del usuario en Keycloak. */
        boolean activo
) {}
