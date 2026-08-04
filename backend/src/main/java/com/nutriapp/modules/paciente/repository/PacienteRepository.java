package com.nutriapp.modules.paciente.repository;

import com.nutriapp.modules.paciente.entity.Paciente;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    Optional<Paciente> findByIdAndNutricionistaIdAndDeletedAtIsNull(UUID id, UUID nutricionistaId);

    boolean existsByNutricionistaIdAndEmailIgnoreCaseAndDeletedAtIsNull(UUID nutricionistaId, String email);

    /**
     * Borrado físico de los pacientes de una nutricionista. Sólo lo usa la baja definitiva del
     * admin, que exige que no haya ninguna receta: un paciente sin recetas no es más que una ficha
     * de contacto que le pertenece a ella y a nadie más.
     */
    void deleteByNutricionistaId(UUID nutricionistaId);

    /**
     * Lista paginada del nutricionista, con búsqueda opcional sin tilde (unaccent) sobre
     * nombre/apellido/email. `q` null o vacío → todos.
     */
    @Query("""
            SELECT p FROM Paciente p
            WHERE p.nutricionistaId = :nutricionistaId
              AND p.deletedAt IS NULL
              AND (:q IS NULL OR :q = ''
                   OR LOWER(FUNCTION('unaccent', CONCAT(p.nombre, ' ', p.apellido, ' ', p.email)))
                      LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%'))))
            """)
    Page<Paciente> search(@Param("nutricionistaId") UUID nutricionistaId,
                          @Param("q") String q,
                          Pageable pageable);
}
