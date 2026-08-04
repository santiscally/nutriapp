package com.nutriapp.modules.producto.dto;

/**
 * Foto del catálogo para el panel del admin. Son los números que hay que mirar después de correr
 * un sync o una importación para saber si salió lo esperado.
 *
 * @param total          todo lo que bajó de Contabilium.
 * @param publicados     lo que la nutricionista puede recetar hoy.
 * @param sinMaestro     están en el ERP pero no en el Excel de TBC: se recetan sin categoría,
 *                       laboratorio, imagen ni tags. No es un error, es trabajo pendiente del
 *                       cliente sobre su planilla.
 * @param bloqueados     marcados BLOQUEADO en el maestro.
 * @param noPublicados   {@code total - publicados}, por cualquiera de las cinco reglas.
 */
public record CatalogoResumenResponse(
        long total,
        long publicados,
        long noPublicados,
        long sinMaestro,
        long bloqueados
) {}
