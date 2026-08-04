package com.nutriapp.modules.receta;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros técnicos de recetas (vigencia, tope de items). Los porcentajes de negocio (descuento y
 * comisión) viven en cada nutricionista, los setea el admin — ver {@code ParametrosNegocioService}.
 */
@ConfigurationProperties(prefix = "nutriapp.recetas")
public record RecetaProperties(
        int vigenciaDias,
        int maxItems
) {}
