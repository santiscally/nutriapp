package com.nutriapp.integrations.contabilium;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de Contabilium (ERP, solo lectura). Contrato según su colección Postman oficial
 * (ver instrucciones_claude/03-integraciones-apis.md §1).
 * Impl real (Fase 1.6): {@link HttpContabiliumClient} con token manager 24h + throttle 15 req/10s.
 * La conciliación de catálogo por SKU que consume {@link #buscarConceptos} se arma en Fase 2 (tarea 2.9).
 */
public interface ContabiliumClient {

    /** GET /api/usuarios/obtenerinfo — health-check / validación de credenciales. */
    CompanyInfo obtenerInfo();

    /**
     * GET /api/conceptos/search?filtro=&page= — listado/búsqueda paginada de productos ("conceptos").
     * Envelope {@code {Items, TotalPage, TotalItems}}, 50 ítems/página fijo. {@code filtro} vacío = todos.
     * ⚠️ Los nombres exactos de los params ({@code filtro}/{@code page}) se validan contra una cuenta
     * real en Fase 2 (la docu no es 100% explícita).
     */
    ConceptoPage buscarConceptos(String filtro, int page);

    record CompanyInfo(String razonSocial, String cuit) {}

    /** Subset del "concepto" (producto) que usamos para conciliar. Nombre/Código vienen en MAYÚSCULAS. */
    record Concepto(
            Long id,
            String tipo,
            String nombre,
            String codigo,
            String descripcion,
            String estado,
            BigDecimal precio,
            BigDecimal precioFinal,
            Integer stock
    ) {}

    /** Página del envelope de {@code /conceptos/search}. */
    record ConceptoPage(List<Concepto> items, int totalPage, int totalItems) {}
}
