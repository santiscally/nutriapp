package com.bonosapp.modules.receta.dto;

/**
 * Resultado de una corrida de resync de cupones (2.8).
 *
 * @param intentados    recetas PENDIENTES con cupón sin sincronizar que se procesaron
 * @param sincronizados cuántas lograron registrar el cupón en TiendaNube en esta corrida
 * @param pendientes    cuántas siguen sin cupón sincronizado tras la corrida (en stub = intentados)
 */
public record ResyncCuponesResponse(int intentados, int sincronizados, long pendientes) {}
