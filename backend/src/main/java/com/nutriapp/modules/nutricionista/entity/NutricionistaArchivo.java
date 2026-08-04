package com.nutriapp.modules.nutricionista.entity;

import com.nutriapp.common.entity.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Archivo de una nutricionista (C-08 matrícula / C-17 foto). Los bytes viven en la DB para que
 * entren en el backup que ya existe, en vez de sumar un volumen aparte que respaldar.
 *
 * <p>{@code contenido} es {@code LAZY}: los listados de nutricionistas no tienen por qué arrastrar
 * megas de PDFs — sólo se carga cuando alguien pide el archivo puntual.
 */
@Getter
@Setter
@Entity
@Table(name = "nutricionista_archivos")
public class NutricionistaArchivo extends BaseEntity {

    private UUID nutricionistaId;

    @Enumerated(EnumType.STRING)
    private TipoArchivo tipo;

    private String nombreOriginal;

    private String contentType;

    private Integer tamanoBytes;

    /**
     * Sin {@code @Lob} a propósito: en Hibernate 6, {@code @Lob byte[]} sobre Postgres se mapea a
     * {@code oid} (large object) y falla contra una columna {@code BYTEA} con "column is of type
     * bytea but expression is of type bigint". {@code VARBINARY} es el que mapea a bytea.
     */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    private byte[] contenido;
}
