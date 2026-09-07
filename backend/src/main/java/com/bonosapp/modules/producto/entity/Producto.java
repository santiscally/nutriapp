package com.bonosapp.modules.producto.entity;

import com.bonosapp.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Getter
@Setter
@Entity
@Table(name = "productos")
public class Producto extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private OrigenProducto origen = OrigenProducto.SEED;

    private Long tiendanubeProductId;

    /** Variante TiendaNube — necesaria para restringir cupones por producto (Fase 2). */
    private Long tiendanubeVariantId;

    private Long contabiliumId;

    /** Clave natural de conciliación TiendaNube ↔ Contabilium. */
    private String sku;

    private String nombre;

    private String descripcion;

    /** Código de barras del ERP (Contabilium {@code CodigoBarras}). Buscable: se escanea o se tipea. */
    private String codigoBarras;

    private BigDecimal precio;

    private Integer stock;

    @Column(name = "imagen_url")
    private String imagenUrl;

    /**
     * Derivado — NO se setea a mano: lo recalcula {@link com.bonosapp.modules.producto.service.PublicacionPolicy}
     * a partir de los campos de las dos fuentes. Ver 07-maestro-articulos-y-catalogo.md §3.2.
     */
    private boolean publicado = true;

    /** Marca/línea comercial. En productos de Contabilium se puebla desde el Subrubro. */
    private String marca;

    // --- Fuente: Contabilium (sync) ---

    /** {@code Tipo} del ERP: "Producto" / "Servicio". Solo los Producto se recetan (mail 2026-08-03). */
    private String tipoErp;

    /** {@code Estado} del ERP mapeado a booleano (C-14). Desconocido → true (defensivo). */
    private boolean activoErp = true;

    /** Nombre del Rubro de Contabilium (ej. "Producto terminado"). Dato, no filtro de la UI. */
    private String rubro;

    /** {@code IdRubro} del ERP. Filtro de ingreso al catálogo: solo los rubros permitidos entran. */
    private String rubroId;

    // --- Fuente: Excel maestro de TBC (import) ---

    private String departamento;

    /** CATEGORIA del maestro (23 valores). Ojo: hasta V009 esta columna guardaba el rubro del ERP. */
    private String categoria;

    private String subcategoria;

    /** ELABORADOR / FABRICANTE del maestro. */
    private String laboratorio;

    /** DESCRIPCION WEB del maestro — texto largo para el "más info" del buscador. */
    private String descripcionWeb;

    /** ESTADO = BLOQUEADO en el maestro: no se muestra en bonosapp. */
    private boolean bloqueadoMaestro;

    /**
     * TAGS TIENDANUBE del maestro. Reemplazo completo en cada import (no merge).
     *
     * <p>{@code @BatchSize}: el buscador devuelve 20 productos por página y cada uno tiene sus tags;
     * sin esto son 20 queries extra por búsqueda, y la búsqueda es la pantalla más usada de la app.
     */
    @BatchSize(size = 100)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "producto_tags", joinColumns = @JoinColumn(name = "producto_id"))
    private Set<ProductoTag> tags = new LinkedHashSet<>();

    private Instant maestroSyncedAt;

    // --- Sin fuente hoy: el maestro no los trae (ver 07-...md §2.4) ---

    private String principioActivo;

    private String presentacion;

    private Instant lastSyncedAt;
}
