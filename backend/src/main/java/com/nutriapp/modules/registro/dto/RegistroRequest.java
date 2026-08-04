package com.nutriapp.modules.registro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud pública de alta de nutricionista (queda PENDIENTE hasta aprobación admin).
 *
 * <p>C-08 (call 40:05–42:34): mail y celular obligatorios sí o sí (Leo), más DNI, matrícula, CUIT
 * y condición fiscal. Viaja como la parte JSON de un multipart, junto al archivo de la matrícula.
 */
public record RegistroRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Teléfono debe ser un número válido (ej. +5491155551234)")
        String telefono,
        @NotBlank String matricula,

        @NotBlank(message = "El DNI es obligatorio")
        @Pattern(regexp = "^[0-9]{7,9}$", message = "El DNI debe tener entre 7 y 9 dígitos, sin puntos")
        String dni,

        @NotBlank(message = "El CUIT es obligatorio")
        @Pattern(regexp = "^[0-9]{2}-?[0-9]{8}-?[0-9]$",
                message = "El CUIT debe tener 11 dígitos (ej. 27-12345678-4)")
        String cuit,

        @NotBlank(message = "Elegí tu condición fiscal")
        String condicionFiscal,

        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password
) {
    /** CUIT sin guiones: se guarda normalizado para que dos formatos no parezcan dos personas. */
    public String cuitNormalizado() {
        return cuit == null ? null : cuit.replace("-", "");
    }
}
