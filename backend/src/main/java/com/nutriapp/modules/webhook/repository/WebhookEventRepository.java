package com.nutriapp.modules.webhook.repository;

import com.nutriapp.modules.webhook.entity.OrigenWebhook;
import com.nutriapp.modules.webhook.entity.WebhookEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /** Guard de idempotencia: ¿ya recibimos este evento? (espeja el UNIQUE de la tabla). */
    boolean existsByOrigenAndEventoAndRecursoId(OrigenWebhook origen, String evento, Long recursoId);

    /** Lote de eventos aún sin procesar, más viejos primero (los drena el WebhookProcessor). */
    List<WebhookEvent> findByProcesadoFalseAndDeletedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
