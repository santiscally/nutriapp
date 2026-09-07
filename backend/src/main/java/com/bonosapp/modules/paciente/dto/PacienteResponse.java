package com.bonosapp.modules.paciente.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PacienteResponse(
        UUID id,
        String nombre,
        String apellido,
        String email,
        String whatsapp,
        LocalDate fechaNacimiento,
        String notas,
        Instant createdAt
) {}
