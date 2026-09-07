package com.bonosapp.modules.producto.service;

import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.contabilium.ContabiliumClient;
import com.bonosapp.integrations.contabilium.ContabiliumClient.Concepto;
import com.bonosapp.integrations.contabilium.ContabiliumClient.ConceptoPage;
import com.bonosapp.integrations.contabilium.ContabiliumClient.RubrosLookup;
import com.bonosapp.integrations.health.IntegrationHealthRegistry;
import com.bonosapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.bonosapp.modules.producto.dto.SyncProductosResponse;
import com.bonosapp.modules.producto.entity.OrigenProducto;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sincronización del catálogo local desde Contabilium (tarea 2.9), conciliando por SKU. El catálogo
 * ya vive en la DB y se usa siempre desde ahí (no depende de la API en runtime); esto sólo lo
 * refresca: manual (admin) u, en live, nightly.
 *
 * <p>En modo stub {@link ContabiliumClient#buscarConceptos} lanza {@link IntegrationUnavailableException}
 * → propaga como 503 explícito "Contabilium no conectada" (nada se toca). En live recorre las páginas
 * de conceptos y hace upsert por SKU, sellando {@code lastSyncedAt}, y mapea Subrubro→marca (en la
 * cuenta del cliente el subrubro ES la marca comercial) y Rubro→{@code rubro}/{@code rubroId}.
 *
 * <p><b>Sólo escribe los campos cuyo dueño es el ERP.</b> Lo que aporta el maestro de artículos de TBC
 * (categoría, subcategoría, departamento, laboratorio, tags, imagen) lo escribe el importador y este
 * sync no lo toca — ver 07-maestro-articulos-y-catalogo.md §3.2.
 *
 * <p>El método NO es {@code @Transactional}: cada lectura/escritura de {@link ProductoRepository} corre
 * en su propia transacción corta, así el connection pool no queda retenido durante los round-trips HTTP
 * (lección GIA de agotamiento de Hikari). El costo es que un full-scan no es atómico — deseable acá:
 * es idempotente y reanudable, y {@code lastSyncedAt} marca qué quedó al día.
 *
 * <p><b>Asíncrono</b>: el catálogo son ~2266 productos (50 páginas throttled ≈ 30-60s). {@link #syncAsync()}
 * corre en un hilo aparte (return 202 inmediato en el controller) y expone {@link #isSincronizando()} +
 * {@link #getUltimoResultado()} para que el panel de admin muestre progreso/resultado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoSyncService {

    private final ContabiliumClient contabiliumClient;
    private final ProductoRepository productoRepository;
    private final IntegrationHealthRegistry health;
    private final PublicacionPolicy publicacionPolicy;

    private final AtomicBoolean sincronizando = new AtomicBoolean(false);
    private volatile String ultimoResultado;

    private enum Resultado { CREADO, ACTUALIZADO, SIN_CAMBIOS, SALTADO }

    public boolean isSincronizando() {
        return sincronizando.get();
    }

    public String getUltimoResultado() {
        return ultimoResultado;
    }

    /**
     * Dispara la sync en background. Guard: si ya hay una en curso, no arranca otra (no-op).
     * Cross-bean call desde el controller → el proxy de Spring aplica {@code @Async}.
     */
    @Async
    public void syncAsync() {
        if (!sincronizando.compareAndSet(false, true)) {
            log.info("[contabilium-sync] ya hay una sincronización en curso, se ignora el disparo");
            return;
        }
        try {
            SyncProductosResponse r = sync();
            ultimoResultado = String.format("revisados=%d creados=%d actualizados=%d sin-cambios=%d",
                    r.revisados(), r.creados(), r.actualizados(), r.sinCambios());
            log.info("[contabilium-sync] OK {}", ultimoResultado);
        } catch (RuntimeException ex) {
            ultimoResultado = "error: " + ex.getMessage();
            log.warn("[contabilium-sync] falló: {}", ex.getMessage());
        } finally {
            sincronizando.set(false);
        }
    }

    public SyncProductosResponse sync() {
        Instant syncedAt = Instant.now();
        int revisados = 0;
        int creados = 0;
        int actualizados = 0;
        int sinCambios = 0;
        try {
            // Rubros/subrubros una sola vez por sync (rubro=categoría, subrubro=marca).
            RubrosLookup lk = contabiliumClient.rubrosLookup();
            // Una vez por sync, no por producto: son ~2266 y el valor no cambia durante la corrida.
            boolean catalogoMapeado = productoRepository.existsByTiendanubeProductIdIsNotNullAndDeletedAtIsNull();
            int page = 1;
            while (true) {
                ConceptoPage cp = contabiliumClient.buscarConceptos("", page);
                for (Concepto c : cp.items()) {
                    revisados++;
                    switch (conciliar(c, lk, syncedAt, catalogoMapeado)) {
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
    private Resultado conciliar(Concepto c, RubrosLookup lk, Instant syncedAt, boolean catalogoMapeado) {
        String sku = c.codigo();
        if (sku == null || sku.isBlank()) {
            log.warn("[contabilium-sync] concepto sin código/SKU (id={}), se saltea", c.id());
            return Resultado.SALTADO;
        }

        Producto existente = productoRepository.findBySkuAndDeletedAtIsNull(sku).orElse(null);
        Producto p = existente != null ? existente : nuevo(sku);

        boolean cambio = aplicar(p, c, lk, catalogoMapeado);
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

    /**
     * Copia los campos de negocio; devuelve true si algún valor cambió (ignora lastSyncedAt).
     *
     * <p>Escribe <b>sólo los campos cuyo dueño es Contabilium</b>. Los que aporta el maestro de TBC
     * (categoría, subcategoría, departamento, laboratorio, descripción web, imagen, tags) no se tocan
     * acá aunque estén vacíos — si no, cada sync borraría lo importado. Ver 07-...md §3.2.
     */
    private boolean aplicar(Producto p, Concepto c, RubrosLookup lk, boolean catalogoMapeado) {
        // precioFinal (con impuestos) es el que ve el paciente; si falta, caemos al precio base.
        BigDecimal precio = c.precioFinal() != null ? c.precioFinal() : c.precio();
        // Subrubro→marca (en esta cuenta el subrubro ES la marca comercial — Gon lo reconfirmó en el
        // mail del 2026-08-03). El rubro va a su propia columna: desde V009 dejó de ser la "categoría"
        // de la UI (valía "Producto terminado" para el 99,8% del catálogo) y pasó a ser filtro de ingreso.
        // Los ids se chequean contra null ANTES de buscar: el lookup puede ser un Map inmutable
        // (RubrosLookup.vacio(), el fallback de HttpContabiliumClient cuando falla /rubros) y
        // Map.of().get(null) tira NPE — un concepto sin rubro tumbaría la sync entera.
        String rubro = c.idRubro() == null ? null : lk.rubros().get(c.idRubro());
        String marca = c.idSubrubro() == null ? null : lk.subrubros().get(c.idSubrubro());
        boolean activoErp = !esInactivo(c.estado());
        boolean cambio = false;
        cambio |= !Objects.equals(p.getNombre(), c.nombre());
        cambio |= !Objects.equals(p.getDescripcion(), c.descripcion());
        cambio |= !Objects.equals(p.getCodigoBarras(), c.codigoBarras());
        cambio |= !Objects.equals(p.getPrecio(), precio);
        cambio |= !Objects.equals(p.getStock(), c.stock());
        cambio |= !Objects.equals(p.getRubro(), rubro);
        cambio |= !Objects.equals(p.getRubroId(), c.idRubro());
        cambio |= !Objects.equals(p.getTipoErp(), c.tipo());
        cambio |= !Objects.equals(p.getMarca(), marca);
        cambio |= p.isActivoErp() != activoErp;
        p.setNombre(c.nombre());
        p.setDescripcion(c.descripcion());
        p.setCodigoBarras(c.codigoBarras());
        p.setPrecio(precio);
        p.setStock(c.stock());
        p.setRubro(rubro);
        p.setRubroId(c.idRubro());
        p.setTipoErp(c.tipo());
        p.setMarca(marca);
        p.setActivoErp(activoErp);
        // Derivado de todo lo anterior + de lo que haya dejado el maestro. Una sola definición
        // compartida con el importador (PublicacionPolicy).
        cambio |= publicacionPolicy.aplicar(p, catalogoMapeado);
        return cambio;
    }

    /**
     * C-14 — ¿el concepto está dado de baja en el ERP? Contabilium manda "Activo"/"Inactivo".
     * Ante un valor desconocido o nulo asumimos activo: preferimos publicar de más antes que
     * vaciar el catálogo si el ERP cambia el vocabulario.
     */
    private boolean esInactivo(String estado) {
        return estado != null && "inactivo".equalsIgnoreCase(estado.trim());
    }
}
