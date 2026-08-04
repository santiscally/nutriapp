package com.nutriapp.integrations.keycloak;

import com.nutriapp.common.error.ConflictException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente de la Admin REST API de Keycloak. Se autentica con <b>client_credentials</b> del
 * service-account de un client confidencial del realm {@code nutriapp} (por defecto
 * {@code nutriapp-backend}), scopeado sólo a {@code realm-management}
 * ({@code manage-users}/{@code view-users}). Opera sobre el realm {@code nutriapp}.
 *
 * <p>Registro de nutricionista: crea el usuario DESHABILITADO y le asigna el rol compuesto
 * {@code NUTRICIONISTA} (que arrastra los client roles del token). Al aprobar, se habilita.
 *
 * <p>Hardening Fase 3 (DIARIO 2026-07-22, hecho 2026-07-27): antes usaba el superusuario del
 * realm master (password grant admin/admin) — privilegio excesivo si el backend se compromete.
 * Ahora el service-account sólo puede administrar usuarios del realm nutriapp.
 */
@Slf4j
@Component
public class KeycloakAdminClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final KeycloakAdminProperties props;
    private final RestClient http;

    // Cache del token de admin: evita un password-grant contra el superusuario en cada llamada.
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public KeycloakAdminClient(KeycloakAdminProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    /**
     * Crea un usuario deshabilitado con password permanente y le asigna el rol NUTRICIONISTA.
     * @return el id (sub) del usuario Keycloak creado.
     * @throws ConflictException si el email ya existe en el realm (409).
     */
    public String registrarNutricionista(String email, String nombre, String apellido, String password) {
        String token = adminToken();
        String userId = crearUsuario(token, email, nombre, apellido, password);
        asignarRolRealm(token, userId, props.nutricionistaRole());
        return userId;
    }

    /** Habilita (o deshabilita) un usuario. Se usa al aprobar/rechazar un registro. */
    public void setEnabled(String userId, boolean enabled) {
        String token = adminToken();
        try {
            http.put()
                    .uri("/admin/realms/{realm}/users/{id}", props.realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", enabled))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new KeycloakAdminException("No se pudo actualizar el usuario en Keycloak", ex);
        }
    }

    /** Borra un usuario. Best-effort: se usa para compensar si el registro local falla luego de crearlo. */
    public void deleteUser(String userId) {
        try {
            deleteUserOrFail(userId);
        } catch (Exception ex) {
            // No re-lanzar: es compensación. Dejamos rastro para limpieza manual si hiciera falta.
            log.error("No se pudo borrar el usuario Keycloak {} en compensación: {}", userId, ex.getMessage());
        }
    }

    /**
     * Borra un usuario propagando el error. Es la variante para la baja definitiva que dispara el
     * admin: si Keycloak no lo borra, la operación tiene que fallar entera — un usuario que sigue
     * existiendo allá y no acá es alguien que puede loguearse sin perfil.
     *
     * <p>Un 404 se toma como éxito: el objetivo es que no exista, y ya no existe.
     */
    public void deleteUserOrFail(String userId) {
        try {
            http.delete()
                    .uri("/admin/realms/{realm}/users/{id}", props.realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("El usuario Keycloak {} ya no existía al borrarlo", userId);
        } catch (HttpClientErrorException ex) {
            throw new KeycloakAdminException("No se pudo borrar el usuario en Keycloak", ex);
        }
    }

    /**
     * Reemplaza la contraseña de un usuario por una permanente (sin obligarlo a cambiarla al
     * entrar). Es la única vía de recuperación que tiene el sistema: no hay "olvidé mi contraseña"
     * por email, así que sin esto una nutricionista que se equivoca queda afuera para siempre.
     */
    public void resetPassword(String userId, String password) {
        try {
            http.put()
                    .uri("/admin/realms/{realm}/users/{id}/reset-password", props.realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("type", "password", "value", password, "temporary", false))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new KeycloakAdminException("No se pudo cambiar la contraseña en Keycloak", ex);
        }
    }

    /**
     * Limpia el contador de intentos fallidos de la protección de fuerza bruta. Se llama al
     * resetear una contraseña: si el usuario quedó frenado por reintentar, la contraseña nueva no
     * le serviría de nada hasta que expire el bloqueo.
     */
    public void limpiarIntentosFallidos(String userId) {
        try {
            http.delete()
                    .uri("/admin/realms/{realm}/attack-detection/brute-force/users/{id}", props.realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // Accesorio: si falla, la contraseña igual quedó cambiada.
            log.warn("No se pudieron limpiar los intentos fallidos de {}: {}", userId, ex.getMessage());
        }
    }

    /**
     * Valida una contraseña haciendo un login real (ROPC) contra el client público del front.
     * Se usa para exigir la contraseña actual antes de cambiarla desde el perfil: Keycloak no
     * expone un "verificar credencial" en la Admin API, y el reset a secas dejaría que cualquiera
     * con la sesión abierta de otro le cambie la clave.
     *
     * @return true si las credenciales son válidas.
     */
    public boolean passwordEsValida(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.publicClientId());
        form.add("username", username);
        form.add("password", password);
        try {
            http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException ex) {
            return false;
        }
    }

    // --- internos ---

    private synchronized String adminToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        try {
            Map<?, ?> resp = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object token = resp != null ? resp.get("access_token") : null;
            if (token == null) {
                throw new KeycloakAdminException("Keycloak no devolvió access_token de admin", null);
            }
            long expiresIn = resp.get("expires_in") instanceof Number n ? n.longValue() : 60L;
            cachedToken = token.toString();
            // Margen de seguridad de 15s para no usar un token a punto de expirar.
            tokenExpiry = Instant.now().plusSeconds(Math.max(10L, expiresIn - 15L));
            return cachedToken;
        } catch (HttpClientErrorException ex) {
            throw new KeycloakAdminException("No se pudo autenticar contra Keycloak Admin", ex);
        }
    }

    private String crearUsuario(String token, String email, String nombre, String apellido, String password) {
        Map<String, Object> body = Map.of(
                "username", email,
                "email", email,
                "firstName", nombre,
                "lastName", apellido,
                "enabled", false,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false)));
        try {
            URI location = http.post()
                    .uri("/admin/realms/{realm}/users", props.realm())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
            if (location == null) {
                throw new KeycloakAdminException("Keycloak no devolvió la ubicación del usuario creado", null);
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict ex) {
            // Mensaje genérico e idéntico al del pre-check local: no revelar qué existe (enumeración).
            throw new ConflictException("Ese email ya está registrado");
        } catch (HttpClientErrorException ex) {
            throw new KeycloakAdminException("No se pudo crear el usuario en Keycloak", ex);
        }
    }

    private void asignarRolRealm(String token, String userId, String roleName) {
        try {
            Map<?, ?> role = http.get()
                    .uri("/admin/realms/{realm}/roles/{role}", props.realm(), roleName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            http.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new KeycloakAdminException("No se pudo asignar el rol " + roleName + " en Keycloak", ex);
        }
    }
}
