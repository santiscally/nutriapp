package com.nutriapp.integrations.keycloak;

/** Falla al operar contra la Admin API de Keycloak (server caído, credenciales, etc.). */
public class KeycloakAdminException extends RuntimeException {
    public KeycloakAdminException(String message, Throwable cause) {
        super(message, cause);
    }
}
