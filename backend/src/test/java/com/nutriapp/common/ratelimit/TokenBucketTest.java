package com.nutriapp.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenBucketTest {

    @Test
    void agotaLaRafagaYLuegoBloquea() {
        TokenBucket bucket = new TokenBucket(3, 1000, 0);

        assertThat(bucket.tryConsume(0)).isTrue();
        assertThat(bucket.tryConsume(0)).isTrue();
        assertThat(bucket.tryConsume(0)).isTrue();
        assertThat(bucket.tryConsume(0)).isFalse(); // 4ta en la misma instantánea → bloqueada
    }

    @Test
    void recargaProporcionalAlTiempo() {
        TokenBucket bucket = new TokenBucket(3, 1000, 0);
        bucket.tryConsume(0);
        bucket.tryConsume(0);
        bucket.tryConsume(0); // 0 tokens

        // +500ms sobre una ventana de 1000ms para 3 tokens → +1.5 tokens
        assertThat(bucket.tryConsume(500)).isTrue();
        assertThat(bucket.tryConsume(500)).isFalse(); // quedaba ~0.5

        // pasada la ventana completa vuelve a full
        assertThat(bucket.tryConsume(2000)).isTrue();
    }

    @Test
    void noSuperaLaCapacidadAlRecargar() {
        TokenBucket bucket = new TokenBucket(2, 1000, 0);
        bucket.tryConsume(0); // queda 1

        assertThat(bucket.refillAndIsFull(0)).isFalse();
        assertThat(bucket.refillAndIsFull(10_000)).isTrue(); // recarga tope a 2, no a 22
    }
}
