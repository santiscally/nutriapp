package com.bonosapp.modules.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de una liquidación. {@code omitidas} lleva el motivo de cada receta que no se pudo
 * liquidar (no existe, o no estaba APLICADA) — el admin tiene que ver qué quedó afuera y por qué,
 * no un conteo mudo.
 */
public record LiquidacionResponse(
        int liquidadas,
        BigDecimal comisionTotal,
        Instant liquidadaAt,
        List<Omitida> omitidas
) {
    public record Omitida(UUID recetaId, String codigo, String motivo) {}
}
