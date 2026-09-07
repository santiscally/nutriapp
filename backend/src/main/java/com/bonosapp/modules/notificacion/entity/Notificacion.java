package com.bonosapp.modules.notificacion.entity;

import com.bonosapp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Notificación encolada (hoy sólo mail): la emisión de una receta hacia el paciente y los
 * avisos del alta pública de una nutricionista (acuse, aprobación, rechazo y el aviso al admin).
 * Se crea en QUEUED; el {@code NotificacionDispatcher} la drena contra el port correspondiente.
 * En modo stub el envío degrada y la notificación sigue QUEUED (se reintenta al pasar la
 * integración a live) — nunca aborta la operación que la originó.
 */
@Getter
@Setter
@Entity
@Table(name = "notificaciones")
public class Notificacion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private TipoNotificacion tipo = TipoNotificacion.EMISION_RECETA;

    private UUID recetaId;

    private UUID nutricionistaId;

    @Enumerated(EnumType.STRING)
    private CanalNotificacion canal;

    private String destinatario;

    private String asunto;

    @Column(columnDefinition = "text")
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    private EstadoNotificacion estado = EstadoNotificacion.QUEUED;

    private int intentos = 0;

    @Column(columnDefinition = "text")
    private String lastError;

    private Instant sentAt;

    /** Marca el envío como exitoso y limpia el último error. */
    public void marcarEnviada() {
        this.estado = EstadoNotificacion.SENT;
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    /** Registra un intento fallido; el dispatcher decide si sigue QUEUED o pasa a FAILED. */
    public void registrarFallo(String error) {
        this.intentos++;
        this.lastError = error;
    }
}
