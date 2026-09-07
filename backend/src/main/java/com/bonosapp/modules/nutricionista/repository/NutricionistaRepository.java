package com.bonosapp.modules.nutricionista.repository;

import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NutricionistaRepository extends JpaRepository<Nutricionista, UUID> {

    Optional<Nutricionista> findByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    Optional<Nutricionista> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /** C-08: guard de duplicados por DNI en el registro público. */
    Optional<Nutricionista> findByDniAndDeletedAtIsNull(String dni);

    /**
     * Bandeja de validación admin: filtro opcional por estado y búsqueda sin tilde
     * (unaccent) sobre nombre/apellido/email. `estado`/`q` null → sin filtrar.
     */
    @Query("""
            SELECT n FROM Nutricionista n
            WHERE n.deletedAt IS NULL
              AND (:estado IS NULL OR n.estadoValidacion = :estado)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(FUNCTION('unaccent', CONCAT(n.nombre, ' ', n.apellido, ' ', n.email)))
                      LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%'))))
            """)
    Page<Nutricionista> search(@Param("estado") EstadoValidacion estado,
                               @Param("q") String q,
                               Pageable pageable);
}
