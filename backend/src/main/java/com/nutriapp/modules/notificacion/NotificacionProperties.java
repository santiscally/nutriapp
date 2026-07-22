package com.nutriapp.modules.notificacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del dispatcher de notificaciones. En stub, los envíos degradan y la
 * notificación sigue QUEUED sin consumir intentos → el poller la reintenta al pasar a live.
 */
@ConfigurationProperties(prefix = "nutriapp.notificaciones")
public record NotificacionProperties(
        long dispatchIntervalMs,
        int maxIntentos,
        int batchSize
) {}
