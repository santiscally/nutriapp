package com.nutriapp.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contraseña nueva que el admin le pone a una nutricionista que no puede entrar.
 *
 * <p>Mismo mínimo de 8 caracteres que el registro: si acá fuera más laxo, el reset sería la puerta
 * de atrás para saltearse la política.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password
) {}
