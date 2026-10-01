package com.bonosapp.modules.receta;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parámetros técnicos de recetas: el descuento es del producto y la comisión, de la nutricionista. */
@ConfigurationProperties(prefix = "bonosapp.recetas")
public record RecetaProperties(
        int vigenciaDias,
        int maxItems
) {}
