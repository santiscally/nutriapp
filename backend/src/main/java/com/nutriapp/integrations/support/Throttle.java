package com.nutriapp.integrations.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Rate limiter de ventana deslizante. {@link #acquire()} bloquea hasta que haya un permiso
 * disponible, garantizando que no se otorguen más de {@code maxPermits} permisos dentro de
 * cualquier ventana de {@code windowMillis}.
 *
 * <p>Contabilium exige throttling del lado del cliente: en Argentina el límite es 25 req/10 s y
 * excederlo dispara un <b>bloqueo por IP de ~1 minuto</b> que afecta TODO lo que salga de esa IP
 * —incluida la facturación del propio cliente si comparte red— (ver
 * {@code instrucciones_claude/03-integraciones-apis.md} §1). Por eso nos quedamos holgados en
 * 15 req/10 s.
 *
 * <p>El reloj y el sleeper son inyectables para poder testear la lógica sin dormir de verdad.
 * Thread-safe: {@link #acquire()} está sincronizado.
 */
public class Throttle {

    private final int maxPermits;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private final Deque<Long> grants = new ArrayDeque<>();

    public Throttle(int maxPermits, long windowMillis) {
        this(maxPermits, windowMillis, System::currentTimeMillis, Sleeper.DEFAULT);
    }

    public Throttle(int maxPermits, long windowMillis, LongSupplier clock, Sleeper sleeper) {
        if (maxPermits < 1) {
            throw new IllegalArgumentException("maxPermits debe ser >= 1");
        }
        if (windowMillis < 1) {
            throw new IllegalArgumentException("windowMillis debe ser >= 1");
        }
        this.maxPermits = maxPermits;
        this.windowMillis = windowMillis;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** Bloquea hasta que se pueda otorgar un permiso sin superar el límite de la ventana. */
    public synchronized void acquire() {
        long now = clock.getAsLong();
        evictExpired(now);
        while (grants.size() >= maxPermits) {
            long oldest = grants.peekFirst();
            long waitMs = windowMillis - (now - oldest);
            if (waitMs > 0) {
                sleepQuietly(waitMs);
            }
            now = clock.getAsLong();
            evictExpired(now);
        }
        grants.addLast(now);
    }

    private void evictExpired(long now) {
        while (!grants.isEmpty() && now - grants.peekFirst() >= windowMillis) {
            grants.pollFirst();
        }
    }

    private void sleepQuietly(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Throttle interrumpido mientras esperaba un permiso", e);
        }
    }
}
