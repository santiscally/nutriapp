package com.bonosapp.modules.notificacion.dto;

import java.time.Instant;

/**
 * Vista de una notificación en el detalle de receta (contrato 05-api-endpoints §Recetas).
 * El front sólo necesita canal/estado/sentAt; el cuerpo y el destinatario no se exponen.
 */
public record NotificacionResponse(
        String canal,
        String estado,
        Instant sentAt
) {}
