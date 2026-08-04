package com.nutriapp.modules.nutricionista.entity;

import com.nutriapp.common.entity.BaseEntity;
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
@Table(name = "nutricionistas")
public class Nutricionista extends BaseEntity {

    /** sub del usuario Keycloak. NULL hasta el primer login (se linkea por email). */
    private String keycloakUserId;

    private String nombre;

    private String apellido;

    private String email;

    private String telefono;

    private String matricula;

    // --- C-08: datos que pidieron en la call (40:05–42:34) ---

    /** Gon: la mejor forma de detectar que una persona no esté cargada dos veces. */
    private String dni;

    /** Sólo CUIT: el CUIL es para relación de dependencia (aclaración de Gon, 42:13). */
    private String cuit;

    /** Responsable Inscripto / Monotributo / Exento… lista cerrada en el front. */
    private String condicionFiscal;

    @Enumerated(EnumType.STRING)
    private EstadoValidacion estadoValidacion = EstadoValidacion.PENDIENTE;

    private Instant validadoAt;

    private String validadoPor;

    private String notasValidacion;

    /**
     * C-01 — % de descuento propio de esta nutricionista. {@code null} = usa el global
     * ({@code configuracion_sistema}). Lo setea sólo el admin.
     */
    private BigDecimal descuentoPct;

    /** C-01 — % de comisión propio. {@code null} = usa el global. Lo setea sólo el admin. */
    private BigDecimal comisionPct;
}
