package com.bonosapp.modules.notificacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros del dispatcher de notificaciones. En stub, los envíos degradan y la
 * notificación sigue QUEUED sin consumir intentos → el poller la reintenta al pasar a live.
 *
 * <p>{@code adminEmail} es la casilla que recibe el aviso de cada solicitud de registro nueva;
 * vacía = nadie se entera (se loguea un warn). {@code appUrl} es la URL pública desde la que se
 * arman los links de los mails.
 */
@ConfigurationProperties(prefix = "bonosapp.notificaciones")
public record NotificacionProperties(
        long dispatchIntervalMs,
        int maxIntentos,
        int batchSize,
        String adminEmail,
        String appUrl
) {
    public boolean tieneAdminEmail() {
        return adminEmail != null && !adminEmail.isBlank();
    }

    /** Sin barra final: los templates concatenan la ruta. */
    public String appUrlNormalizada() {
        if (appUrl == null || appUrl.isBlank()) {
            return "";
        }
        return appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }
}
