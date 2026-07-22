package com.nutriapp.modules.registro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Solicitud pública de alta de nutricionista (queda PENDIENTE hasta aprobación admin). */
public record RegistroRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Teléfono debe ser un número válido (ej. +5491155551234)")
        String telefono,
        @NotBlank String matricula,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password
) {}
