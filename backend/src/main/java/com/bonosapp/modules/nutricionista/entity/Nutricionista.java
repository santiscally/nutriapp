package com.bonosapp.modules.nutricionista.entity;

import com.bonosapp.common.entity.BaseEntity;
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
     * % de descuento de las recetas de esta nutricionista. Lo setea sólo el admin.
     *
     * <p>V011: obligatorio. Antes era nullable y {@code null} significaba "usá el global de
     * {@code configuracion_sistema}", tabla que dejó de existir — el dato vive en un solo lugar.
     * En el alta lo completa {@code NutricionistaProperties}; después lo ajusta el admin.
     */
    @Column(nullable = false)
    private BigDecimal descuentoPct;

    /** % de comisión de esta nutricionista. Obligatorio (V011). Lo setea sólo el admin. */
    @Column(nullable = false)
    private BigDecimal comisionPct;

    /**
     * ¿Puede loguearse? Espejo local del {@code enabled} de Keycloak, que es quien realmente
     * decide (V012). Vive acá para que la bandeja del admin muestre el estado de una página entera
     * sin pedirle una fila por request a Keycloak.
     *
     * <p>Es independiente de {@link #estadoValidacion}: una nutricionista APROBADA puede estar dada
     * de baja sin que haya que mentirle al historial marcándola como RECHAZADA.
     */
    @Column(nullable = false)
    private boolean activo;
}
