package com.nutriapp.modules.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * C-05 — marcar recetas como liquidadas (comisión pagada a la nutricionista).
 * Se liquida por receta, aunque la pantalla del cierre sea mensual (call 57:54).
 */
public record LiquidarRecetasRequest(
        @NotEmpty(message = "Hay que indicar al menos una receta") List<UUID> recetaIds
) {}
