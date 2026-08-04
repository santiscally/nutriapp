package com.nutriapp.modules.nutricionista;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Porcentajes con los que nace una nutricionista recién registrada.
 *
 * <p>No son una configuración global de negocio (esa se eliminó en V011): son sólo el punto de
 * partida del alta. El registro es público y la nutricionista no elige su propio descuento ni su
 * comisión, así que alguien tiene que poner el primer número; el admin lo ajusta al aprobarla, y
 * desde ese momento el valor que manda es el de su ficha.
 */
@ConfigurationProperties(prefix = "nutriapp.nutricionistas")
public record NutricionistaProperties(
        BigDecimal descuentoPctDefault,
        BigDecimal comisionPctDefault
) {}
