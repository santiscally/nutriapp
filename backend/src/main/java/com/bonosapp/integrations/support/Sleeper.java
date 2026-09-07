package com.bonosapp.integrations.support;

/**
 * Abstracción del "dormir" para poder testear backoff/throttling sin esperas reales.
 * Producción usa {@link #DEFAULT} (Thread.sleep); los tests inyectan un no-op o un
 * sleeper que avanza un reloj simulado.
 */
@FunctionalInterface
public interface Sleeper {

    Sleeper DEFAULT = Thread::sleep;

    void sleep(long millis) throws InterruptedException;
}
