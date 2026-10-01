package com.bonosapp.modules.nutricionista;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Comisión con la que nace una nutricionista recién registrada; después la ajusta el admin. */
@ConfigurationProperties(prefix = "bonosapp.nutricionistas")
public record NutricionistaProperties(
        BigDecimal comisionPctDefault
) {}
