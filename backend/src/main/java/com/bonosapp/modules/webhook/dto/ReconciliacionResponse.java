package com.bonosapp.modules.webhook.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de barrer las órdenes pagadas de una ventana y aplicar las que matcheen un bono.
 *
 * @param bonosAplicados los que pasaron a APLICADO en esta corrida (vacío si no había nada colgado).
 */
public record ReconciliacionResponse(
        int horas,
        Instant desde,
        int ordenesRevisadas,
        int bonosAplicados,
        List<String> codigos,
        String mensaje
) {}
