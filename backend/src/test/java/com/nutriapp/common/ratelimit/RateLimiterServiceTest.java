package com.nutriapp.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutriapp.common.ratelimit.RateLimitProperties.Bucket;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    private final long[] now = {0};

    private RateLimiterService service(boolean enabled) {
        RateLimitProperties props = new RateLimitProperties(enabled, new Bucket(2, 60), new Bucket(5, 60));
        return new RateLimiterService(props, () -> now[0]);
    }

    @Test
    void limitaPorClienteYBucket() {
        RateLimiterService svc = service(true);

        assertThat(svc.tryAcquire("registro", "1.1.1.1")).isTrue();
        assertThat(svc.tryAcquire("registro", "1.1.1.1")).isTrue();
        assertThat(svc.tryAcquire("registro", "1.1.1.1")).isFalse(); // 3ra supera capacidad 2

        // otro cliente tiene su propio cupo
        assertThat(svc.tryAcquire("registro", "2.2.2.2")).isTrue();

        // otro bucket (webhooks, capacidad 5) es independiente del de registro
        assertThat(svc.tryAcquire("webhooks", "1.1.1.1")).isTrue();
    }

    @Test
    void recuperaCupoTrasLaVentana() {
        RateLimiterService svc = service(true);
        svc.tryAcquire("registro", "9.9.9.9");
        svc.tryAcquire("registro", "9.9.9.9");
        assertThat(svc.tryAcquire("registro", "9.9.9.9")).isFalse();

        now[0] = 60_000; // pasó la ventana → recarga a full
        assertThat(svc.tryAcquire("registro", "9.9.9.9")).isTrue();
    }

    @Test
    void deshabilitadoSiemprePermite() {
        RateLimiterService svc = service(false);
        for (int i = 0; i < 10; i++) {
            assertThat(svc.tryAcquire("registro", "1.1.1.1")).isTrue();
        }
    }

    @Test
    void bucketDesconocidoNoSeLimita() {
        RateLimiterService svc = service(true);
        for (int i = 0; i < 10; i++) {
            assertThat(svc.tryAcquire("desconocido", "1.1.1.1")).isTrue();
        }
    }

    @Test
    void evictIdleLiberaBucketsLlenos() {
        RateLimiterService svc = service(true);
        svc.tryAcquire("registro", "1.1.1.1"); // crea el bucket (queda 1/2)
        now[0] = 120_000; // recarga a full → inactivo
        svc.evictIdle();
        // Tras la evicción, el cliente arranca de cero otra vez con su ráfaga completa.
        assertThat(svc.tryAcquire("registro", "1.1.1.1")).isTrue();
        assertThat(svc.tryAcquire("registro", "1.1.1.1")).isTrue();
        assertThat(svc.tryAcquire("registro", "1.1.1.1")).isFalse();
    }
}
