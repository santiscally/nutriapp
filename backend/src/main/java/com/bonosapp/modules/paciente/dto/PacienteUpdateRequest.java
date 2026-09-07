package com.bonosapp.modules.paciente.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record PacienteUpdateRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "WhatsApp debe ser un número válido (ej. +5491144443333)")
        String whatsapp,
        LocalDate fechaNacimiento,
        String notas
) {}
