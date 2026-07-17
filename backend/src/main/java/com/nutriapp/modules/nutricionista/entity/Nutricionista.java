package com.nutriapp.modules.nutricionista.entity;

import com.nutriapp.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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

    @Enumerated(EnumType.STRING)
    private EstadoValidacion estadoValidacion = EstadoValidacion.PENDIENTE;

    private Instant validadoAt;

    private String validadoPor;

    private String notasValidacion;
}
