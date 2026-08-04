package com.nutriapp.integrations.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config del cliente de administración de Keycloak (Admin REST API). A diferencia de las
 * 4 integraciones externas (stub|live), Keycloak siempre está presente en el stack: es
 * nuestro propio servidor de identidad. Se usa para crear/habilitar usuarios al registrar
 * y aprobar nutricionistas.
 *
 * <p>Se autentica con <b>client_credentials</b> del service-account de {@code clientId} (un client
 * confidencial del realm {@code nutriapp}, por defecto {@code nutriapp-backend}), scopeado sólo a
 * los roles {@code realm-management} {@code manage-users}/{@code view-users}. NO usa el superusuario
 * del realm master (hardening Fase 3): si el backend se compromete, el alcance es mínimo.
 */
@ConfigurationProperties(prefix = "nutriapp.keycloak")
public record KeycloakAdminProperties(
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret,
        String nutricionistaRole,
        /**
         * Client público del front (ROPC). Sólo se usa para verificar la contraseña actual cuando
         * alguien la cambia desde su perfil: la Admin API no tiene un "validar credencial".
         */
        String publicClientId
) {}
