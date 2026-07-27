package com.nutriapp.modules.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Request del simulador de dev: fabrica una orden pagada de TiendaNube que usó el cupón de una receta.
 * Todo salvo el código es opcional (se derivan defaults). Sólo perfil dev.
 */
public record SimularOrdenRequest(
        @NotBlank String recetaCodigo,
        Integer ordenNumero,
        Long ordenTiendanubeId,
        BigDecimal ordenTotal
) {}
