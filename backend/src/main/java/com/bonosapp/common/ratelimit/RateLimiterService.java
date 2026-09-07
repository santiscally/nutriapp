package com.bonosapp.common.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Rate limiter en memoria (token bucket por IP y por bucket). Es single-instance: alcanza para el
 * MVP con una sola instancia del backend. Si se escala a N instancias, migrar a un store compartido
 * (Redis / Bucket4j distribuido) — documentado como TODO de escalado.
 */
@Service
public class RateLimiterService {

    private final RateLimitProperties props;
    private final LongSupplier clock;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterService(RateLimitProperties props) {
        this(props, System::currentTimeMillis);
    }

    /** Constructor para tests: reloj inyectable (sin dormir de verdad). */
    RateLimiterService(RateLimitProperties props, LongSupplier clock) {
        this.props = props;
        this.clock = clock;
    }

    /**
     * @return true si se permite la request; false si {@code clientId} superó el límite del bucket.
     *         Si el rate limit está apagado o el bucket no está configurado, siempre permite.
     */
    public boolean tryAcquire(String bucketName, String clientId) {
        if (!props.enabled()) {
            return true;
        }
        RateLimitProperties.Bucket cfg = props.bucketFor(bucketName);
        if (cfg == null) {
            return true;
        }
        long now = clock.getAsLong();
        TokenBucket bucket = buckets.computeIfAbsent(
                bucketName + "|" + clientId,
                k -> new TokenBucket(cfg.capacity(), cfg.windowSeconds() * 1000L, now));
        return bucket.tryConsume(now);
    }

    /**
     * Libera los buckets inactivos (llenos tras recargar) para acotar la memoria: una IP que dejó de
     * pegar vuelve a capacidad y se descarta; si vuelve, se recrea al full. Inofensivo perder alguno.
     */
    @Scheduled(fixedDelayString = "${bonosapp.rate-limit.evict-interval-ms:600000}")
    public void evictIdle() {
        long now = clock.getAsLong();
        buckets.values().removeIf(bucket -> bucket.refillAndIsFull(now));
    }
}
