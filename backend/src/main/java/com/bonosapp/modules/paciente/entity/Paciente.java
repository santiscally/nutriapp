package com.bonosapp.modules.paciente.entity;

import com.bonosapp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pacientes")
public class Paciente extends BaseEntity {

    /** Dueño. Scoping duro: cada nutricionista ve SOLO sus pacientes. */
    @Column(name = "nutricionista_id", nullable = false)
    private UUID nutricionistaId;

    private String nombre;

    private String apellido;

    /** Canal de entrega de la receta: obligatorio. */
    private String email;

    /** Canal de entrega de la receta: obligatorio (E.164, ej. +5491144443333). */
    private String whatsapp;

    private LocalDate fechaNacimiento;

    private String notas;
}
