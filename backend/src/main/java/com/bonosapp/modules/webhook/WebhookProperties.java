package com.bonosapp.modules.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del procesamiento de webhooks y del polling de respaldo de TiendaNube.
 * En stub, la lectura de la orden degrada y el evento queda sin procesar → se reintenta.
 */
@ConfigurationProperties(prefix = "bonosapp.webhooks")
public record WebhookProperties(
        /** Cada cuánto el processor drena la cola de eventos sin procesar. */
        long processIntervalMs,
        int batchSize,
        /** Cada cuánto corre el polling de respaldo de órdenes pagadas. */
        long pollingIntervalMs,
        /** Ventana hacia atrás que barre cada polling (idempotente: ya-aplicadas se saltean). */
        int pollingWindowHours
) {}
