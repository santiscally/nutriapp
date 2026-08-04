package com.nutriapp.modules.receta.entity;

import com.nutriapp.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "recetas")
public class Receta extends BaseEntity {

    /** Código único generado por nosotros (ej. RX-7K2M4X). ES el código de cupón. */
    private String codigo;

    private UUID nutricionistaId;

    private UUID pacienteId;

    @Enumerated(EnumType.STRING)
    private EstadoReceta estado = EstadoReceta.PENDIENTE;

    private BigDecimal descuentoPct;

    private Instant emitidaAt;

    private LocalDate venceAt;

    private Instant aplicadaAt;

    private Instant anuladaAt;

    // --- Cupón TiendaNube ---
    private Long cuponTiendanubeId;

    @Enumerated(EnumType.STRING)
    private CuponSyncEstado cuponSyncEstado = CuponSyncEstado.PENDIENTE;

    private String cuponSyncError;

    // --- Conversión (snapshot al aplicar) ---
    private Long ordenTiendanubeId;

    private Integer ordenNumero;

    private BigDecimal ordenTotal;

    private Instant ordenPaidAt;

    private BigDecimal comisionPct;

    private BigDecimal comisionMonto;

    /** C-05: cuándo el admin liquidó (pagó) la comisión de esta receta. null = todavía no. */
    private Instant liquidadaAt;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecetaItem> items = new ArrayList<>();

    public void addItem(RecetaItem item) {
        item.setReceta(this);
        this.items.add(item);
    }
}
