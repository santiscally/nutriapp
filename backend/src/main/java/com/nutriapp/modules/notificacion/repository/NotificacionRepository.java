package com.nutriapp.modules.notificacion.repository;

import com.nutriapp.modules.notificacion.entity.EstadoNotificacion;
import com.nutriapp.modules.notificacion.entity.Notificacion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    /** Notificaciones de una receta, para el detalle (orden estable por canal). */
    List<Notificacion> findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(UUID recetaId);

    /** Lote a drenar por el dispatcher: QUEUED con reintentos por debajo del tope. */
    List<Notificacion> findByEstadoAndIntentosLessThanAndDeletedAtIsNullOrderByCreatedAtAsc(
            EstadoNotificacion estado, int maxIntentos, Pageable pageable);
}
