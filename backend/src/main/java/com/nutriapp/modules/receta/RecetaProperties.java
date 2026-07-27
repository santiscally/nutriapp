package com.nutriapp.modules.receta;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros técnicos de recetas (vigencia, tope de items). Los porcentajes de negocio (descuento y
 * comisión) se movieron a la DB, editables por el admin — ver {@code modules/configuracion}.
 */
@ConfigurationProperties(prefix = "nutriapp.recetas")
public record RecetaProperties(
        int vigenciaDias,
        int maxItems
) {}
