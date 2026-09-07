package com.bonosapp.integrations;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config de las 3 integraciones externas. `mode` decide qué adapter se registra:
 * `stub` (default) o `live`. Flip por env sin tocar código (ver CLAUDE.md "Regla de oro").
 *
 * <p>WhatsApp salió de acá en la tarea 2.4: el envío pasó a ser un link {@code wa.me} que abre la
 * nutricionista, así que no hay proveedor, ni credenciales, ni modo que configurar.
 */
@ConfigurationProperties(prefix = "bonosapp.integrations")
public record IntegrationsProperties(
        Contabilium contabilium,
        TiendaNube tiendanube,
        Mail mail
) {
    public record Contabilium(String mode, String baseUrl, String clientId, String clientSecret) {}

    public record TiendaNube(String mode, String baseUrl, String storeId, String accessToken,
                             String clientId, String clientSecret, String userAgent,
                             String webhookSecret, String storeUrl) {

        /** Vitrina pública a la que se manda a la paciente. Vacía = los mensajes van sin link. */
        public String storeUrlNormalizada() {
            if (storeUrl == null || storeUrl.isBlank()) {
                return null;
            }
            String u = storeUrl.trim();
            return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
        }
    }

    public record Mail(String mode, String fromAddress, String fromName) {}

    public static boolean isLive(String mode) {
        return "live".equalsIgnoreCase(mode);
    }
}
