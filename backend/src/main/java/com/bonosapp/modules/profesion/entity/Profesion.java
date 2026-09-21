package com.bonosapp.modules.profesion.entity;

import com.bonosapp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "profesiones")
public class Profesion extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String nombre;

    /** Sacar una profesión de la lista sin borrar el dato de quienes ya la eligieron. */
    @Column(nullable = false)
    private boolean activo = true;
}
