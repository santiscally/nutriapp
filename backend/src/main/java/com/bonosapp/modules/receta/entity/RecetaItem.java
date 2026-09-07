package com.bonosapp.modules.receta.entity;

import com.bonosapp.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "receta_items")
public class RecetaItem extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "receta_id")
    private Receta receta;

    private UUID productoId;

    private Integer cantidad = 1;

    /** Snapshot del precio del producto al emitir. */
    private BigDecimal precioLista;

    private String indicaciones;
}
