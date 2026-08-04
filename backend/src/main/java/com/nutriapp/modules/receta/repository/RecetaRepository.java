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

    /**
     * Todas las recetas de una nutricionista, incluidas las soft-deleted: se usa como guard antes
     * de borrarla definitivamente. El filtro por deleted_at sería peor que inútil acá — una fila
     * soft-deleted sigue teniendo la FK viva y rompería el DELETE igual, además de ser plata que
     * alguna vez se contabilizó.
     */
    long countByNutricionistaId(UUID nutricionistaId);

    /** Guard del borrado de paciente: ¿tiene recetas en un estado dado? (409 si PENDIENTE). */
    boolean existsByPacienteIdAndEstadoAndDeletedAtIsNull(UUID pacienteId, EstadoReceta estado);

    /** Job de vencimiento: recetas PENDIENTES cuya vigencia ya pasó (global, todos los nutris). */
    List<Receta> findByEstadoAndVenceAtBeforeAndDeletedAtIsNull(EstadoReceta estado, java.time.LocalDate fecha);

    /**
     * Resync de cupones (2.8): recetas todavía PENDIENTES cuyo cupón no llegó a sincronizarse
     * (quedó PENDIENTE o falló con ERROR). Sólo tiene sentido reintentar mientras la receta siga
     * PENDIENTE — una APLICADA/VENCIDA/ANULADA ya no necesita cupón. Global (todos los nutris).
     */
    @Query("""
            SELECT r FROM Receta r
            WHERE r.deletedAt IS NULL
              AND r.estado = com.nutriapp.modules.receta.entity.EstadoReceta.PENDIENTE
              AND r.cuponSyncEstado IN (com.nutriapp.modules.receta.entity.CuponSyncEstado.PENDIENTE,
                                        com.nutriapp.modules.receta.entity.CuponSyncEstado.ERROR)
            ORDER BY r.emitidaAt ASC
            """)
    List<Receta> findResyncables(Pageable pageable);

    @Query("""
            SELECT COUNT(r) FROM Receta r
            WHERE r.deletedAt IS NULL
              AND r.estado = com.nutriapp.modules.receta.entity.EstadoReceta.PENDIENTE
              AND r.cuponSyncEstado IN (com.nutriapp.modules.receta.entity.CuponSyncEstado.PENDIENTE,
                                        com.nutriapp.modules.receta.entity.CuponSyncEstado.ERROR)
            """)
    long countResyncables();

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

    /**
     * Recetas convertidas del nutricionista en una ventana.
     *
     * <p><b>C-04:</b> la ventana es por {@code ordenPaidAt} — la fecha en que el paciente pagó en
     * TiendaNube — y no por emisión ni por cuándo procesamos el webhook. Una receta emitida el 29/07
     * y pagada en agosto comisiona en el cierre de <b>agosto</b> (call 51:33).
     *
     * <p><b>C-05:</b> cuenta APLICADA y LIQUIDADA: liquidar es haberle pagado a la nutricionista, no
     * deshace la conversión. Los cierres históricos tienen que seguir mostrando la receta.
     */
    @Query("""
            SELECT COUNT(r) FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado IN (com.nutriapp.modules.receta.entity.EstadoReceta.APLICADA,
                               com.nutriapp.modules.receta.entity.EstadoReceta.LIQUIDADA)
              AND r.ordenPaidAt >= :desde AND r.ordenPaidAt < :hasta
            """)
    long countConvertidasEntre(@Param("nutricionistaId") UUID nutricionistaId,
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

    /** Comisión de las convertidas en la ventana (mismas reglas C-04/C-05 que arriba). */
    @Query("""
            SELECT COALESCE(SUM(r.comisionMonto), 0) FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado IN ('APLICADA','LIQUIDADA')
              AND r.ordenPaidAt >= :desde AND r.ordenPaidAt < :hasta
            """)
    BigDecimal sumComisionEntre(@Param("nutricionistaId") UUID nutricionistaId,
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

    /** Cierre mensual: detalle de las convertidas en la ventana. */
    @Query("""
            SELECT r FROM Receta r
            WHERE r.nutricionistaId = :nutricionistaId
              AND r.deletedAt IS NULL
              AND r.estado IN ('APLICADA','LIQUIDADA')
              AND r.ordenPaidAt >= :desde AND r.ordenPaidAt < :hasta
            ORDER BY r.ordenPaidAt DESC
            """)
    List<Receta> findConvertidasEntre(@Param("nutricionistaId") UUID nutricionistaId,
                                      @Param("desde") Instant desde,
                                      @Param("hasta") Instant hasta);

    /**
     * C-05 — pendientes de liquidar: recetas ya convertidas a las que todavía no se les pagó la
     * comisión. Global (todas las nutricionistas), acotado por fecha de pago en TiendaNube.
     * Es lo que alimenta el cierre consolidado del admin (C-06).
     */
    @Query("""
            SELECT r FROM Receta r
            WHERE r.deletedAt IS NULL
              AND r.estado = com.nutriapp.modules.receta.entity.EstadoReceta.APLICADA
              AND r.ordenPaidAt >= :desde AND r.ordenPaidAt < :hasta
              AND (:nutricionistaId IS NULL OR r.nutricionistaId = :nutricionistaId)
            ORDER BY r.nutricionistaId, r.ordenPaidAt
            """)
    List<Receta> findLiquidables(@Param("nutricionistaId") UUID nutricionistaId,
                                 @Param("desde") Instant desde,
                                 @Param("hasta") Instant hasta);

    /** Liquidación por lote: trae sólo las que existen y siguen vivas. */
    List<Receta> findByIdInAndDeletedAtIsNull(java.util.Collection<UUID> ids);

    /**
     * C-06 — todas las recetas convertidas de la ventana, de todas las nutricionistas, para el
     * cierre consolidado del admin. Mismas reglas que el cierre individual: ventana por
     * {@code ordenPaidAt} (C-04) y cuenta APLICADA + LIQUIDADA (C-05).
     */
    @Query("""
            SELECT r FROM Receta r
            WHERE r.deletedAt IS NULL
              AND r.estado IN ('APLICADA','LIQUIDADA')
              AND r.ordenPaidAt >= :desde AND r.ordenPaidAt < :hasta
            ORDER BY r.nutricionistaId, r.ordenPaidAt
            """)
    List<Receta> findConvertidasEntreTodas(@Param("desde") Instant desde,
                                           @Param("hasta") Instant hasta);
}
