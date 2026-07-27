package com.nutriapp.integrations.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Verifica la ventana deslizante sin dormir de verdad: reloj y sleeper simulados
 * (el sleeper avanza el reloj lo que "durmió").
 */
class ThrottleTest {

    private static Sleeper advancingSleeper(AtomicLong clock, List<Long> sleeps) {
        return millis -> {
            sleeps.add(millis);
            clock.addAndGet(millis);
        };
    }

    @Test
    void otorgaHastaElMaximoSinDormir_luegoEsperaAQueLaVentanaDeslice() {
        AtomicLong now = new AtomicLong(0);
        List<Long> sleeps = new ArrayList<>();
        Throttle t = new Throttle(3, 1000, now::get, advancingSleeper(now, sleeps));

        t.acquire();
        t.acquire();
        t.acquire();
        assertThat(sleeps).isEmpty();

        // El 4º permiso no entra hasta que el más viejo (t=0) sale de la ventana de 1000ms.
        t.acquire();
        assertThat(sleeps).containsExactly(1000L);
        assertThat(now.get()).isEqualTo(1000);
    }

    @Test
    void esperaSoloElRestoDeLaVentana() {
        AtomicLong now = new AtomicLong(0);
        List<Long> sleeps = new ArrayList<>();
        Throttle t = new Throttle(1, 100, now::get, advancingSleeper(now, sleeps));

        t.acquire();          // t=0
        now.set(30);          // pasaron 30ms
        t.acquire();          // debe esperar el resto: 100 - 30 = 70

        assertThat(sleeps).containsExactly(70L);
        assertThat(now.get()).isEqualTo(100);
    }

    @Test
    void llamadasEspaciadasNoDisparanEspera() {
        AtomicLong now = new AtomicLong(0);
        List<Long> sleeps = new ArrayList<>();
        Throttle t = new Throttle(2, 100, now::get, advancingSleeper(now, sleeps));

        t.acquire();          // t=0
        now.set(200);         // muy separadas: la ventana ya venció
        t.acquire();
        now.set(400);
        t.acquire();

        assertThat(sleeps).isEmpty();
    }
}
