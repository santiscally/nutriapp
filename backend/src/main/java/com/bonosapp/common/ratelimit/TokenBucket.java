package com.bonosapp.common.ratelimit;

/**
 * Token bucket clásico para una clave (bucket + cliente). El tiempo se pasa por parámetro
 * ({@code nowMillis}) en vez de leer un reloj real: hace la lógica 100% determinística y testeable.
 * Los métodos son {@code synchronized} porque una misma IP puede llegar por varios hilos a la vez.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerMilli;
    private double tokens;
    private long lastMillis;

    TokenBucket(int capacity, long windowMillis, long nowMillis) {
        this.capacity = capacity;
        this.refillPerMilli = (double) capacity / windowMillis;
        this.tokens = capacity;
        this.lastMillis = nowMillis;
    }

    /** Consume un token si hay; devuelve true si la request se permite. */
    synchronized boolean tryConsume(long nowMillis) {
        refill(nowMillis);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** true si tras recargar el bucket está lleno (cliente inactivo) → candidato a evicción. */
    synchronized boolean refillAndIsFull(long nowMillis) {
        refill(nowMillis);
        return tokens >= capacity;
    }

    private void refill(long nowMillis) {
        if (nowMillis > lastMillis) {
            tokens = Math.min(capacity, tokens + (nowMillis - lastMillis) * refillPerMilli);
            lastMillis = nowMillis;
        }
    }
}
