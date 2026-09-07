package com.bonosapp.modules.producto;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglas de qué entra al catálogo recetable. Van por configuración y no hardcodeadas porque el cliente
 * las sigue ajustando: Gon pasó el rubro 144331 ("producto terminado") en el mail del 2026-08-03 pero
 * quedó en mandar la lista completa (call 34:26).
 *
 * @param rubrosPermitidos    IdRubro de Contabilium que cuentan como producto terminado. Vacío = sin
 *                            filtro de rubro (útil si el cliente reorganiza el ERP y no queremos vaciar
 *                            el catálogo de un sync para el otro).
 * @param tiposErpPermitidos  {@code Tipo} del ERP que se recetean. <b>Es una lista y no un valor único
 *                            a propósito</b>: en la cuenta de TBC el campo tiene <i>tres</i> valores —
 *                            Producto (2004), Combo (209) y Servicio (54)— así que "solo Producto"
 *                            también deja afuera los combos. Agregar {@code Combo} es cambiar un env
 *                            var, no tocar código. Vacío = sin filtro de tipo.
 * @param precioMinimo        Umbral de precio irrisorio: por debajo, el artículo está dado de baja de
 *                            hecho (tienen ~1000 cargados a $1 — call 44:52).
 * @param importMaxBytes      Tope del .xlsx que sube el admin. El maestro real pesa ~1,5 MB.
 */
@ConfigurationProperties(prefix = "bonosapp.catalogo")
public record CatalogoProperties(
        List<String> rubrosPermitidos,
        List<String> tiposErpPermitidos,
        BigDecimal precioMinimo,
        long importMaxBytes
) {
    public CatalogoProperties {
        rubrosPermitidos = rubrosPermitidos == null ? List.of() : List.copyOf(rubrosPermitidos);
        tiposErpPermitidos = tiposErpPermitidos == null ? List.of() : List.copyOf(tiposErpPermitidos);
        precioMinimo = precioMinimo == null ? BigDecimal.ZERO : precioMinimo;
        importMaxBytes = importMaxBytes <= 0 ? 20L * 1024 * 1024 : importMaxBytes;
    }
}
