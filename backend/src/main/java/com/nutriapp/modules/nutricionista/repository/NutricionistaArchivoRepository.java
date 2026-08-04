package com.nutriapp.modules.nutricionista.repository;

import com.nutriapp.modules.nutricionista.entity.NutricionistaArchivo;
import com.nutriapp.modules.nutricionista.entity.TipoArchivo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NutricionistaArchivoRepository extends JpaRepository<NutricionistaArchivo, UUID> {

    Optional<NutricionistaArchivo> findByNutricionistaIdAndTipoAndDeletedAtIsNull(
            UUID nutricionistaId, TipoArchivo tipo);

    /**
     * ¿Qué nutricionistas de este lote tienen archivo de este tipo? Para pintar el listado sin
     * traerse los bytes de cada una (el listado sólo necesita saber si hay o no).
     */
    @Query("""
            SELECT a.nutricionistaId FROM NutricionistaArchivo a
            WHERE a.deletedAt IS NULL AND a.tipo = :tipo AND a.nutricionistaId IN :ids
            """)
    List<UUID> idsConArchivo(@Param("tipo") TipoArchivo tipo, @Param("ids") List<UUID> ids);
}
