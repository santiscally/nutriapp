package com.nutriapp.modules.configuracion.repository;

import com.nutriapp.modules.configuracion.entity.ConfiguracionSistema;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<ConfiguracionSistema, UUID> {

    /** La fila singleton (la más antigua no borrada; el seed de V004 crea exactamente una). */
    Optional<ConfiguracionSistema> findFirstByDeletedAtIsNullOrderByCreatedAtAsc();
}
