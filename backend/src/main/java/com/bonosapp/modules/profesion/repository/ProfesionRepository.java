package com.bonosapp.modules.profesion.repository;

import com.bonosapp.modules.profesion.entity.Profesion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfesionRepository extends JpaRepository<Profesion, UUID> {

    List<Profesion> findByActivoTrueAndDeletedAtIsNullOrderByNombreAsc();

    /** El front manda el nombre exacto que le devolvió la lista; solo toleramos el casing. */
    @Query("select p from Profesion p where p.deletedAt is null and p.activo = true "
            + "and lower(p.nombre) = lower(:nombre)")
    Optional<Profesion> findActivaPorNombre(@Param("nombre") String nombre);
}
