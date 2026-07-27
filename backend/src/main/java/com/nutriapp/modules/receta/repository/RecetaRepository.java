package com.nutriapp.modules.receta.repository;

import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecetaRepository extends JpaRepository<Receta, UUID> {

    Optional<Receta> findByIdAndNutricionistaIdAndDeletedAtIsNull(UUID id, UUID nutricionistaId);

    boolean existsByCodigo(String codigo);

    /** Matcheo del cupón de una orden pagada → receta (global, sin scope de nutricionista). */
    Optional<Receta> findByCodigoAndDeletedAtIsNull(String codigo);

    long countByNutricionistaIdAndEstadoAndDeletedAtIsNull(UUID nutricionistaId, EstadoReceta estado);

    /** Guard del borrado de paciente: ¿tiene recetas en un estado dado? (409 si PENDIENTE). */
    boolean existsByPacienteIdAndEstadoAndDeletedAtIsNull(UUID pacienteId, EstadoReceta estado);

    /** Job de vencimiento: recetas PENDIENTES cuya vigencia ya pasó (global, todos los nutris). */
    List<Receta> findByEstadoAndVenceAtBeforeAndDeletedAtIsNull(EstadoReceta estado, java.time.LocalDate fecha);

    /** Listado del nutricionista con filtro opcional por estado. */
    @Query("""
            SELECT r FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND (:estado IS NULL OR r.estado = :estado)
            """)
    Page<Receta> search(@Param("nutricionistaId") UUID nutricionistaId,
                        @Param("estado") EstadoReceta estado,
                        Pageable pageable);

    List<Receta> findTop8ByNutricionistaIdAndDeletedAtIsNullOrderByEmitidaAtDesc(UUID nutricionistaId);

    /** Recetas del nutricionista en un estado con aplicada/emitida dentro de una ventana. */
    @Query("""
            SELECT COUNT(r) FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado = :estado
              AND r.aplicadaAt >= :desde AND r.aplicadaAt < :hasta
            """)
    long countAplicadasEntre(@Param("nutricionistaId") UUID nutricionistaId,
                             @Param("estado") EstadoReceta estado,
                             @Param("desde") Instant desde,
                             @Param("hasta") Instant hasta);

    @Query("""
            SELECT COUNT(r) FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado = 'VENCIDA'
              AND r.venceAt >= :desde AND r.venceAt < :hasta
            """)
    long countVencidasEntre(@Param("nutricionistaId") UUID nutricionistaId,
                            @Param("desde") java.time.LocalDate desde,
                            @Param("hasta") java.time.LocalDate hasta);

    @Query("""
            SELECT COALESCE(SUM(r.comisionMonto), 0) FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado = 'APLICADA'
              AND r.aplicadaAt >= :desde AND r.aplicadaAt < :hasta
            """)
    BigDecimal sumComisionEntre(@Param("nutricionistaId") UUID nutricionistaId,
                                @Param("desde") Instant desde,
                                @Param("hasta") Instant hasta);

    @Query("""
            SELECT COALESCE(SUM(r.ordenTotal), 0) FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado = 'APLICADA'
              AND r.aplicadaAt >= :desde AND r.aplicadaAt < :hasta
            """)
    BigDecimal sumVentasEntre(@Param("nutricionistaId") UUID nutricionistaId,
                              @Param("desde") Instant desde,
                              @Param("hasta") Instant hasta);

    /** Cierre mensual: recetas emitidas en la ventana (por emitidaAt). */
    @Query("""
            SELECT COUNT(r) FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.emitidaAt >= :desde AND r.emitidaAt < :hasta
            """)
    long countEmitidasEntre(@Param("nutricionistaId") UUID nutricionistaId,
                            @Param("desde") Instant desde,
                            @Param("hasta") Instant hasta);

    /** Cierre mensual: detalle de las aplicadas (convertidas) en la ventana. */
    @Query("""
            SELECT r FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado = 'APLICADA'
              AND r.aplicadaAt >= :desde AND r.aplicadaAt < :hasta
            ORDER BY r.aplicadaAt DESC
            """)
    List<Receta> findAplicadasEntre(@Param("nutricionistaId") UUID nutricionistaId,
                                    @Param("desde") Instant desde,
                                    @Param("hasta") Instant hasta);
}
