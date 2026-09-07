package com.bonosapp.modules.webhook.exception;

/**
 * Firma HMAC de un webhook inválida o ausente. La firma ES la autenticación del webhook
 * (no hay JWT), así que un fallo se traduce en 401. Nunca se persiste el evento.
 */
public class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException(String message) {
        super(message);
    }
}
