package com.nutriapp.modules.webhook.entity;

import com.nutriapp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento de webhook entrante, persistido para idempotencia y procesamiento async.
 * Unicidad por (origen, evento, recurso_id): TiendaNube repite webhooks — el UNIQUE
 * de la tabla evita reprocesar el mismo evento. El {@code payload} guarda el body crudo (jsonb).
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_events")
public class WebhookEvent extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private OrigenWebhook origen = OrigenWebhook.TIENDANUBE;

    /** Nombre del evento tal cual lo manda el proveedor (ej. "order/paid"). */
    private String evento;

    /** Id del recurso afectado (para order/paid = id de la orden). */
    private Long recursoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    private boolean procesado = false;

    private Instant procesadoAt;

    /** Motivo si el procesamiento terminó sin aplicar (no era nuestro cupón, error, etc.). */
    private String error;

    public void marcarProcesado(String error) {
        this.procesado = true;
        this.procesadoAt = Instant.now();
        this.error = error;
    }
}
