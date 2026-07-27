package com.nutriapp.modules.configuracion.entity;

import com.nutriapp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Parámetros de negocio configurables por el ADMIN (tabla singleton). El % de descuento es global y
 * fijo (el nutricionista no lo edita al emitir); el % de comisión aplica a la conversión de recetas.
 */
@Entity
@Table(name = "configuracion_sistema")
@Getter
@Setter
public class ConfiguracionSistema extends BaseEntity {

    @Column(name = "descuento_pct", nullable = false)
    private BigDecimal descuentoPct;

    @Column(name = "comision_pct", nullable = false)
    private BigDecimal comisionPct;
}
