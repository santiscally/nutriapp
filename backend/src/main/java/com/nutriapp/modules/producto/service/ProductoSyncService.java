package com.nutriapp.modules.producto.service;

import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.contabilium.ContabiliumClient;
import com.nutriapp.integrations.contabilium.ContabiliumClient.Concepto;
import com.nutriapp.integrations.contabilium.ContabiliumClient.ConceptoPage;
import com.nutriapp.integrations.health.IntegrationHealthRegistry;
import com.nutriapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.nutriapp.modules.producto.dto.SyncProductosResponse;
import com.nutriapp.modules.producto.entity.OrigenProducto;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sincronización del catálogo local desde Contabilium (tarea 2.9), conciliando por SKU. El catálogo
 * ya vive en la DB y se usa siempre desde ahí (no depende de la API en runtime); esto sólo lo
 * refresca: manual (admin) u, en live, nightly.
 *
 * <p>En modo stub {@link ContabiliumClient#buscarConceptos} lanza {@link IntegrationUnavailableException}
 * → propaga como 503 explícito "Contabilium no conectada" (nada se toca). En live recorre las páginas
 * de conceptos y hace upsert por SKU, sellando {@code lastSyncedAt}.
 *
 * <p>El método NO es {@code @Transactional}: cada lectura/escritura de {@link ProductoRepository} corre
 * en su propia transacción corta, así el connection pool no queda retenido durante los round-trips HTTP
 * (lección GIA de agotamiento de Hikari). El costo es que un full-scan no es atómico — deseable acá:
 * es idempotente y reanudable, y {@code lastSyncedAt} marca qué quedó al día.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoSyncService {

    private final ContabiliumClient contabiliumClient;
    private final ProductoRepository productoRepository;
    private final IntegrationHealthRegistry health;

    private enum Resultado { CREADO, ACTUALIZADO, SIN_CAMBIOS, SALTADO }

    public SyncProductosResponse sync() {
        Instant syncedAt = Instant.now();
        int revisados = 0;
        int creados = 0;
        int actualizados = 0;
        int sinCambios = 0;
        try {
            int page = 1;
            while (true) {
                ConceptoPage cp = contabiliumClient.buscarConceptos("", page);
                for (Concepto c : cp.items()) {
                    revisados++;
                    switch (conciliar(c, syncedAt)) {
                        case CREADO -> creados++;
                        case ACTUALIZADO -> actualizados++;
                        case SIN_CAMBIOS -> sinCambios++;
                        case SALTADO -> { /* sin SKU: no se puede conciliar, ya se logueó */ }
                    }
                }
                if (cp.items().isEmpty() || page >= cp.totalPage()) {
                    break;
                }
                page++;
            }
            health.registrarExito(Proveedor.CONTABILIUM);
        } catch (IntegrationUnavailableException ex) {
            health.registrarError(Proveedor.CONTABILIUM, ex.getMessage());
            throw ex; // 503 explícito: Contabilium no conectada
        }
        log.info("[contabilium-sync] revisados={} creados={} actualizados={} sin-cambios={}",
                revisados, creados, actualizados, sinCambios);
        return new SyncProductosResponse(revisados, creados, actualizados, sinCambios, syncedAt);
    }

    /** Upsert de un concepto por SKU. Cada save/find corre en su propia tx (repo por defecto). */
    private Resultado conciliar(Concepto c, Instant syncedAt) {
        String sku = c.codigo();
        if (sku == null || sku.isBlank()) {
            log.warn("[contabilium-sync] concepto sin código/SKU (id={}), se saltea", c.id());
            return Resultado.SALTADO;
        }

        Producto existente = productoRepository.findBySkuAndDeletedAtIsNull(sku).orElse(null);
        Producto p = existente != null ? existente : nuevo(sku);

        boolean cambio = aplicar(p, c);
        p.setContabiliumId(c.id());
        p.setLastSyncedAt(syncedAt);
        productoRepository.save(p);

        if (existente == null) {
            return Resultado.CREADO;
        }
        return cambio ? Resultado.ACTUALIZADO : Resultado.SIN_CAMBIOS;
    }

    private Producto nuevo(String sku) {
        Producto p = new Producto();
        p.setSku(sku);
        p.setOrigen(OrigenProducto.CONTABILIUM);
        return p;
    }

    /** Copia los campos de negocio; devuelve true si algún valor cambió (ignora lastSyncedAt). */
    private boolean aplicar(Producto p, Concepto c) {
        // precioFinal (con impuestos) es el que ve el paciente; si falta, caemos al precio base.
        BigDecimal precio = c.precioFinal() != null ? c.precioFinal() : c.precio();
        boolean cambio = false;
        cambio |= !Objects.equals(p.getNombre(), c.nombre());
        cambio |= !Objects.equals(p.getDescripcion(), c.descripcion());
        cambio |= !Objects.equals(p.getPrecio(), precio);
        cambio |= !Objects.equals(p.getStock(), c.stock());
        p.setNombre(c.nombre());
        p.setDescripcion(c.descripcion());
        p.setPrecio(precio);
        p.setStock(c.stock());
        // La publicación (estado activo/inactivo del ERP) se decide en Fase 2 contra una cuenta real;
        // no se toca acá para no despublicar productos por un mapeo de `estado` no confirmado.
        return cambio;
    }
}
