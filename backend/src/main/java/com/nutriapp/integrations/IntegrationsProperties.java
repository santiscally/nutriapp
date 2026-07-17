package com.nutriapp.integrations;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config de las 4 integraciones externas. `mode` decide qué adapter se registra:
 * `stub` (default) o `live`. Flip por env sin tocar código (ver CLAUDE.md "Regla de oro").
 */
@ConfigurationProperties(prefix = "nutriapp.integrations")
public record IntegrationsProperties(
        Contabilium contabilium,
        TiendaNube tiendanube,
        Mail mail,
        WhatsApp whatsapp
) {
    public record Contabilium(String mode, String baseUrl, String clientId, String clientSecret) {}

    public record TiendaNube(String mode, String storeId, String accessToken,
                             String clientId, String clientSecret, String userAgent) {}

    public record Mail(String mode) {}

    public record WhatsApp(String mode, String phoneNumberId, String accessToken) {}

    public static boolean isLive(String mode) {
        return "live".equalsIgnoreCase(mode);
    }
}
