package com.nutriapp.modules.admin.dto;

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
        String estadoValidacion,
        Instant validadoAt,
        String notasValidacion,
        Instant createdAt
) {}
