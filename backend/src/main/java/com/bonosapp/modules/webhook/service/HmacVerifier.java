package com.bonosapp.modules.webhook.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Verificación HMAC-SHA256 del body crudo de un webhook contra el secreto compartido.
 * TiendaNube firma con el {@code client_secret} de la app y manda el digest hex en
 * el header {@code x-linkedstore-hmac-sha256}. Comparación en tiempo constante.
 */
@Component
public class HmacVerifier {

    private static final String ALGO = "HmacSHA256";

    /**
     * @return true sólo si el secreto y la firma provista son válidos y coinciden con el
     *         HMAC del body. Fail-closed: secreto/firma vacíos o hex malformado → false.
     */
    public boolean verify(String secret, byte[] body, String providedHex) {
        if (secret == null || secret.isBlank() || providedHex == null || providedHex.isBlank()) {
            return false;
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedHex.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        byte[] expected = hmac(secret, body == null ? new byte[0] : body);
        // MessageDigest.isEqual es tiempo-constante en JDK moderno (no cortocircuita).
        return MessageDigest.isEqual(expected, provided);
    }

    /** HMAC-SHA256 en hex minúscula del body con el secreto — útil para tests y clientes. */
    public String hex(String secret, byte[] body) {
        return HexFormat.of().formatHex(hmac(secret, body == null ? new byte[0] : body));
    }

    private byte[] hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el HMAC del webhook", e);
        }
    }
}
