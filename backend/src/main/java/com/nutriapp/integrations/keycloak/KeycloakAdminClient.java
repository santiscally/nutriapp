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
 * Cliente de la Admin REST API de Keycloak. Se autentica con las credenciales de admin del
 * realm master (client {@code admin-cli}, password grant) y opera sobre el realm {@code nutriapp}.
 *
 * Registro de nutricionista: crea el usuario DESHABILITADO y le asigna el rol compuesto
 * {@code NUTRICIONISTA} (que arrastra los client roles del token). Al aprobar, se habilita.
 *
 * HARDENING Fase 3 (ver DIARIO 2026-07-22): reemplazar el superusuario master por un
 * service-account confidencial scopeado sólo a los client roles de {@code realm-management}
 * ({@code manage-users}/{@code view-users}) del realm nutriapp, con grant client_credentials.
 * Hoy usa el admin del master (default de dev, override por env) — privilegio excesivo si el
 * backend se compromete.
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
            http.delete()
                    .uri("/admin/realms/{realm}/users/{id}", props.realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // No re-lanzar: es compensación. Dejamos rastro para limpieza manual si hiciera falta.
            log.error("No se pudo borrar el usuario Keycloak {} en compensación: {}", userId, ex.getMessage());
        }
    }

    // --- internos ---

    private synchronized String adminToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.adminClientId());
        form.add("username", props.adminUsername());
        form.add("password", props.adminPassword());
        try {
            Map<?, ?> resp = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", props.adminRealm())
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
