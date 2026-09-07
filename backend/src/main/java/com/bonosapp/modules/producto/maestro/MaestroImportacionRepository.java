package com.bonosapp.modules.producto.maestro;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaestroImportacionRepository extends JpaRepository<MaestroImportacion, UUID> {

    /** Última importación, para el panel de integraciones. */
    Optional<MaestroImportacion> findFirstByOrderByCreatedAtDesc();
}
