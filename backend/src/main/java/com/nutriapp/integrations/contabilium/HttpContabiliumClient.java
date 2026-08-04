package com.nutriapp.integrations.contabilium;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.IntegrationsProperties;
import com.nutriapp.integrations.support.Throttle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Cliente HTTP real de Contabilium (ERP, solo lectura). Se conecta recién en Fase 2: hoy el bean
 * se registra sólo si {@code CONTABILIUM_MODE=live} (ver {@code IntegrationsConfig}).
 *
 * <p><b>Auth</b>: OAuth2 {@code client_credentials} — Email → {@code client_id}, API Key →
 * {@code client_secret} (así lo define su docu). Token ~24h; se cachea y se renueva proactivamente
 * (margen de 30 min) o ante un 401 (reintento único).
 *
 * <p><b>Rate limit (crítico)</b>: en AR el límite es 25 req/10s y excederlo bloquea la IP ~1 min
 * afectando hasta la facturación del cliente. Todo request sale detrás de un {@link Throttle} a
 * 15 req/10s (ver instrucciones_claude/03-integraciones-apis.md §1).
 *
 * <p>Un fallo de red (host caído) se traduce a {@link IntegrationUnavailableException} para que la
 * lógica de negocio degrade con gracia, igual que el stub.
 */
@Slf4j
public class HttpContabiliumClient implements ContabiliumClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    /** Margen para no usar un token a punto de expirar. */
    private static final long EXPIRY_MARGIN_SECONDS = 1800;

    private final IntegrationsProperties.Contabilium props;
    private final RestClient http;
    private final Throttle throttle;
    /**
     * Mapper propio para deserializar el cuerpo desde {@code byte[]}. Contabilium (backend .NET)
     * declara el charset del Content-Type como Windows-1252 pero manda bytes UTF-8 → si dejamos que
     * el converter de RestClient decodifique con ese charset, los nombres con Ñ/acentos salen mojibake
     * (ej. "AÑOS" → "AÃ'OS"). Parsear el byte-stream con Jackson fuerza la autodetección UTF-8 (spec JSON).
     */
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public HttpContabiliumClient(IntegrationsProperties.Contabilium props) {
        this(props, new Throttle(15, 10_000));
    }

    HttpContabiliumClient(IntegrationsProperties.Contabilium props, Throttle throttle) {
        this.props = props;
        this.throttle = throttle;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.http = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public CompanyInfo obtenerInfo() {
        InfoDto dto = authedGet("/api/usuarios/obtenerinfo", InfoDto.class);
        return new CompanyInfo(dto != null ? dto.razonSocial() : null, dto != null ? dto.cuit() : null);
    }

    @Override
    public ConceptoPage buscarConceptos(String filtro, int page) {
        ConceptoPageDto dto = authedGet(
                uri -> uri.path("/api/conceptos/search")
                        .queryParam("filtro", filtro == null ? "" : filtro)
                        .queryParam("page", page)
                        .build(),
                ConceptoPageDto.class);
        List<Concepto> items = dto == null || dto.items() == null
                ? List.of()
                : dto.items().stream().map(HttpContabiliumClient::toConcepto).toList();
        return new ConceptoPage(items, nz(dto == null ? null : dto.totalPage()), nz(dto == null ? null : dto.totalItems()));
    }

    @Override
    public RubrosLookup rubrosLookup() {
        JsonNode arr = rawRubros();
        if (arr == null || !arr.isArray()) {
            return RubrosLookup.vacio();
        }
        Map<String, String> rubros = new HashMap<>();
        Map<String, String> subrubros = new HashMap<>();
        for (JsonNode r : arr) {
            String rid = r.path("Id").asText(null);
            if (rid != null) {
                rubros.put(rid, r.path("Nombre").asText(null));
            }
            JsonNode subs = r.path("SubRubros");
            if (subs.isArray()) {
                for (JsonNode s : subs) {
                    String sid = s.path("Id").asText(null);
                    if (sid != null) {
                        subrubros.put(sid, s.path("Nombre").asText(null));
                    }
                }
            }
        }
        return new RubrosLookup(rubros, subrubros);
    }

    // --- Diagnóstico raw (dev): devuelven el JSON crudo para inspeccionar campos disponibles ---

    /** Página cruda de {@code /api/conceptos/search} (todos los campos, sin mapear a DTO). */
    public JsonNode rawConceptos(int page) {
        return authedGetRaw(uri -> uri.path("/api/conceptos/search")
                .queryParam("filtro", "").queryParam("page", page).build());
    }

    /** Rubros/categorías crudos de {@code /api/conceptos/rubros}. */
    public JsonNode rawRubros() {
        return authedGetRaw(uri -> uri.path("/api/conceptos/rubros")
                .queryParam("includeChilds", true).build());
    }

    private JsonNode authedGetRaw(Function<UriBuilder, URI> uriFn) {
        try {
            byte[] raw = withTokenRetry(() -> {
                throttle.acquire();
                return http.get().uri(uriFn)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .retrieve().body(byte[].class);
            });
            return (raw == null || raw.length == 0) ? null : mapper.readTree(raw);
        } catch (ResourceAccessException ex) {
            throw new IntegrationUnavailableException("contabilium");
        } catch (IOException ex) {
            throw new UncheckedIOException("Contabilium: respuesta no parseable", ex);
        }
    }

    // --- HTTP interno ---

    private <T> T authedGet(String path, Class<T> type) {
        return authedGet(uri -> uri.path(path).build(), type);
    }

    private <T> T authedGet(Function<UriBuilder, URI> uriFn, Class<T> type) {
        try {
            // Traemos el cuerpo como byte[] y lo parseamos nosotros en UTF-8 (ver comentario de `mapper`).
            byte[] raw = withTokenRetry(() -> {
                throttle.acquire();
                return http.get()
                        .uri(uriFn)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .retrieve()
                        .body(byte[].class);
            });
            if (raw == null || raw.length == 0) {
                return null;
            }
            return mapper.readValue(raw, type);
        } catch (ResourceAccessException ex) {
            // Timeout / conexión rechazada: proveedor caído → degradar como el stub.
            log.warn("[contabilium] sin conexión: {}", ex.getMessage());
            throw new IntegrationUnavailableException("contabilium");
        } catch (IOException ex) {
            // Cuerpo ilegible/inesperado: es un bug de contrato, no "caído" → que se vea (no lo enmascaramos como 503).
            throw new UncheckedIOException("Contabilium: respuesta no parseable", ex);
        }
    }

    /** Ejecuta el request; ante 401 invalida el token y reintenta una vez. */
    private <T> T withTokenRetry(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.info("[contabilium] 401 — renovando token y reintentando una vez");
            invalidateToken();
            return call.get();
        }
    }

    private String token() {
        String current = cachedToken;
        if (current != null && Instant.now().isBefore(tokenExpiry)) {
            return current;
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        // Re-check: otro hilo pudo renovarlo mientras esperábamos el lock.
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        throttle.acquire();
        TokenDto resp = http.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenDto.class);
        if (resp == null || resp.accessToken() == null) {
            throw new IllegalStateException("Contabilium no devolvió access_token");
        }
        long expiresIn = resp.expiresIn() != null ? resp.expiresIn() : 86399L;
        cachedToken = resp.accessToken();
        tokenExpiry = Instant.now().plusSeconds(Math.max(60L, expiresIn - EXPIRY_MARGIN_SECONDS));
        return cachedToken;
    }

    private synchronized void invalidateToken() {
        cachedToken = null;
        tokenExpiry = Instant.EPOCH;
    }

    private static Concepto toConcepto(ConceptoDto d) {
        return new Concepto(d.id(), d.tipo(), d.nombre(), d.codigo(), d.codigoBarras(), d.descripcion(),
                d.estado(), d.precio(), d.precioFinal(), d.stock(), d.idRubro(), d.idSubrubro());
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // --- DTOs de deserialización (PascalCase de Contabilium) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenDto(@JsonProperty("access_token") String accessToken,
                            @JsonProperty("expires_in") Long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InfoDto(@JsonProperty("RazonSocial") String razonSocial,
                           @JsonProperty("Cuit") String cuit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConceptoDto(
            @JsonProperty("Id") Long id,
            @JsonProperty("Tipo") String tipo,
            @JsonProperty("Nombre") String nombre,
            @JsonProperty("Codigo") String codigo,
            @JsonProperty("CodigoBarras") String codigoBarras,
            @JsonProperty("Descripcion") String descripcion,
            @JsonProperty("Estado") String estado,
            @JsonProperty("Precio") BigDecimal precio,
            @JsonProperty("PrecioFinal") BigDecimal precioFinal,
            @JsonProperty("Stock") Integer stock,
            @JsonProperty("IdRubro") String idRubro,
            @JsonProperty("IdSubrubro") String idSubrubro) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConceptoPageDto(
            @JsonProperty("Items") List<ConceptoDto> items,
            @JsonProperty("TotalPage") Integer totalPage,
            @JsonProperty("TotalItems") Integer totalItems) {}
}
