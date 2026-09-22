package com.bonosapp.modules.webhook.repository;

import com.bonosapp.modules.webhook.entity.OrigenWebhook;
import com.bonosapp.modules.webhook.entity.WebhookEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /** Guard de idempotencia: ¿ya recibimos este evento? (espeja el UNIQUE de la tabla). */
    boolean existsByOrigenAndEventoAndRecursoId(OrigenWebhook origen, String evento, Long recursoId);

    /**
     * Cuándo llegó el último webhook de este origen. null = <b>nunca llegó ninguno</b>, que es un
     * dato distinto de "no hubo ventas": si hay bonos comprados y esto está en null, el webhook no
     * está llegando y la conversión depende del barrido de respaldo.
     */
    @Query("""
            SELECT MAX(e.createdAt) FROM WebhookEvent e WHERE e.origen = :origen
            """)
    java.time.Instant ultimoRecibido(@Param("origen") OrigenWebhook origen);

    /** Lote de eventos aún sin procesar, más viejos primero (los drena el WebhookProcessor). */
    List<WebhookEvent> findByProcesadoFalseAndDeletedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
