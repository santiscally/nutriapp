package com.bonosapp.modules.nutricionista.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de la propia contraseña desde el perfil.
 *
 * <p>Pide la actual además de la nueva: sin eso, cualquiera que agarre una sesión abierta podría
 * quedarse con la cuenta. La verificación se hace contra Keycloak, que es quien tiene la credencial.
 */
public record CambiarPasswordRequest(
        @NotBlank(message = "Ingresá tu contraseña actual")
        String passwordActual,

        @NotBlank(message = "Ingresá la contraseña nueva")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String passwordNueva
) {}
