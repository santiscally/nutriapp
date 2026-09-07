package com.bonosapp.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de tasa por IP para los endpoints públicos (hardening Fase 3). {@code enabled=false}
 * apaga el control (dev/tests). Cada bucket define {@code capacity} (ráfaga máxima) y
 * {@code windowSeconds} (ventana en la que se recargan esos tokens).
 */
@ConfigurationProperties(prefix = "bonosapp.rate-limit")
public record RateLimitProperties(boolean enabled, Bucket registro, Bucket webhooks) {

    public static final String BUCKET_REGISTRO = "registro";
    public static final String BUCKET_WEBHOOKS = "webhooks";

    public record Bucket(int capacity, int windowSeconds) {}

    /** Config del bucket por nombre, o null si no está configurado (→ no se limita). */
    public Bucket bucketFor(String name) {
        return switch (name) {
            case BUCKET_REGISTRO -> registro;
            case BUCKET_WEBHOOKS -> webhooks;
            default -> null;
        };
    }
}
