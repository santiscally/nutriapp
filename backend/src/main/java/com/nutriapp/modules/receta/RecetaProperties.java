package com.nutriapp.modules.receta;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de negocio de recetas. Valores reales (descuento, comisión) TBD con Gon —
 * ver preguntas abiertas en 04-plan-de-fases.md. Defaults provisorios en application.yml.
 */
@ConfigurationProperties(prefix = "nutriapp.recetas")
public record RecetaProperties(
        int vigenciaDias,
        BigDecimal descuentoDefaultPct,
        BigDecimal comisionPct,
        int maxItems
) {}
