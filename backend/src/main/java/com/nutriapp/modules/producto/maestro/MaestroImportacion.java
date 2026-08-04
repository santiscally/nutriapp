package com.nutriapp.modules.producto.maestro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Registro de cada importación del maestro. Es un log append-only (no extiende {@code BaseEntity}:
 * no se edita ni se borra), y sirve para dos cosas: mostrar "última importación" en el panel de
 * integraciones al lado de la última sync de Contabilium, y poder explicar después por qué un producto
 * quedó como quedó.
 *
 * <p><b>El archivo no se guarda.</b> Solo el resultado: además de las 9 columnas que nutriapp lee, el
 * maestro trae la estructura de costos de TBC y no hay razón para tenerla en esta base.
 */
@Getter
@Setter
@Entity
@Table(name = "maestro_importaciones")
@EntityListeners(AuditingEntityListener.class)
public class MaestroImportacion {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    private String nombreArchivo;

    private Long tamanoBytes;

    private int filasLeidas;

    private int filasMatcheadas;

    private int filasActualizadas;

    private int filasSinMatch;

    private int filasRechazadas;

    /** Resumen legible (SKUs sin match, rechazos) para que el admin sepa qué revisar. */
    @Column(name = "detalle")
    private String detalle;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
