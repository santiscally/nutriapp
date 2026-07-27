package com.nutriapp.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HmacVerifierTest {

    private final HmacVerifier verifier = new HmacVerifier();
    private static final String SECRET = "test-secret";
    private final byte[] body = "{\"store_id\":1,\"event\":\"order/paid\",\"id\":42}".getBytes(StandardCharsets.UTF_8);

    @Test
    void firmaValida_verificaOk() {
        String firma = verifier.hex(SECRET, body);
        assertThat(verifier.verify(SECRET, body, firma)).isTrue();
    }

    @Test
    void firmaValidaEnMayusculas_verificaOk() {
        String firma = verifier.hex(SECRET, body).toUpperCase();
        assertThat(verifier.verify(SECRET, body, firma)).isTrue();
    }

    @Test
    void secretoDistinto_falla() {
        String firma = verifier.hex("otro-secreto", body);
        assertThat(verifier.verify(SECRET, body, firma)).isFalse();
    }

    @Test
    void bodyAlterado_falla() {
        String firma = verifier.hex(SECRET, body);
        byte[] alterado = "{\"store_id\":1,\"event\":\"order/paid\",\"id\":99}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(SECRET, alterado, firma)).isFalse();
    }

    @Test
    void firmaAusenteOSecretoVacio_falla() {
        assertThat(verifier.verify(SECRET, body, null)).isFalse();
        assertThat(verifier.verify(SECRET, body, "  ")).isFalse();
        assertThat(verifier.verify("", body, verifier.hex(SECRET, body))).isFalse();
        assertThat(verifier.verify(null, body, verifier.hex(SECRET, body))).isFalse();
    }

    @Test
    void hexMalformado_falla() {
        assertThat(verifier.verify(SECRET, body, "no-es-hex-zz")).isFalse();
    }
}
