package com.nutriapp.modules.producto.entity;

import com.nutriapp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

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

    private BigDecimal precio;

    private Integer stock;

    @Column(name = "imagen_url")
    private String imagenUrl;

    private boolean publicado = true;

    private String marca;

    private String laboratorio;

    private String principioActivo;

    private String presentacion;

    private Instant lastSyncedAt;
}
