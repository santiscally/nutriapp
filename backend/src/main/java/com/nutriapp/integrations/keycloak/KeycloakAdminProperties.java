package com.nutriapp.integrations.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config del cliente de administración de Keycloak (Admin REST API). A diferencia de las
 * 4 integraciones externas (stub|live), Keycloak siempre está presente en el stack: es
 * nuestro propio servidor de identidad. Se usa para crear/habilitar usuarios al registrar
 * y aprobar nutricionistas.
 */
@ConfigurationProperties(prefix = "nutriapp.keycloak")
public record KeycloakAdminProperties(
        String baseUrl,
        String realm,
        String adminRealm,
        String adminClientId,
        String adminUsername,
        String adminPassword,
        String nutricionistaRole
) {}
