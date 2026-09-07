package com.bonosapp.integrations.tiendanube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.integrations.support.Sleeper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Cliente HTTP real de TiendaNube (API 2025-03). Se conecta recién en Fase 2: hoy el bean se
 * registra sólo si {@code TIENDANUBE_MODE=live} (ver {@code IntegrationsConfig}).
 *
 * <p><b>Auth</b>: el {@code access_token} de OAuth NO expira (se obtiene una vez y se persiste por
 * env). Header {@code Authorization: Bearer} + {@code User-Agent} <b>obligatorio</b> (sin él: 400).
 *
 * <p><b>Rate limit</b>: leaky bucket 40 req / drenaje 2 req/s. Ante 429 hacemos backoff usando el
 * header {@code x-rate-limit-reset} (ms) y reintentamos. Un 5xx o un fallo de red se traducen a
 * {@link IntegrationUnavailableException} para que la lógica degrade con gracia (cupón queda
 * PENDIENTE de sync / el evento de webhook se reintenta), igual que el stub.
 *
 * <p>⚠️ Formato de {@code order.coupon[]}, {@code paid_at} y errores 422: a validar contra la tienda
 * demo en Fase 2 (ver instrucciones_claude/03-integraciones-apis.md §2).
 */
@Slf4j
public class HttpTiendaNubeClient implements TiendaNubeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_BACKOFF_MS = 2000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final IntegrationsProperties.TiendaNube props;
    private final RestClient http;
    private final Sleeper sleeper;

    public HttpTiendaNubeClient(IntegrationsProperties.TiendaNube props) {
        this(props, Sleeper.DEFAULT);
    }

    HttpTiendaNubeClient(IntegrationsProperties.TiendaNube props, Sleeper sleeper) {
        this.props = props;
        this.sleeper = sleeper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.accessToken())
                .build();
    }

    @Override
    public Coupon createCoupon(CouponRequest request) {
        if (request.productIds() == null || request.productIds().isEmpty()) {
            // Sin products[] TiendaNube aplica el cupón a TODA la tienda. Nunca es lo que queremos.
            throw new IllegalArgumentException(
                    "Cupón sin productos: se aplicaría a toda la tienda (receta " + request.code() + ")");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", request.code());
        body.put("type", "percentage");
        body.put("value", request.valuePct().setScale(2, RoundingMode.HALF_UP).toPlainString());
        body.put("max_uses", 1);
        if (request.startDate() != null) {
            body.put("start_date", request.startDate().toString());
        }
        if (request.endDate() != null) {
            body.put("end_date", request.endDate().toString());
        }
        body.put("products", request.productIds());
        CouponDto dto = call(() -> http.post()
                .uri("/{store}/coupons", props.storeId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(CouponDto.class));
        return new Coupon(dto == null ? 0 : dto.id(), dto == null ? null : dto.code(),
                dto != null && Boolean.TRUE.equals(dto.valid()));
    }

    @Override
    public void deleteCoupon(long couponId) {
        call(() -> http.delete()
                .uri("/{store}/coupons/{id}", props.storeId(), couponId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public Order getOrder(long orderId) {
        OrderDto dto = call(() -> http.get()
                .uri("/{store}/orders/{id}", props.storeId(), orderId)
                .retrieve()
                .body(OrderDto.class));
        return toOrder(dto);
    }

    @Override
    public List<Order> getPaidOrdersSince(Instant since) {
        Function<UriBuilder, URI> uri = b -> b.path("/{store}/orders")
                .queryParam("payment_status", "paid")
                .queryParam("updated_at_min", since.toString())
                .build(props.storeId());
        OrderDto[] dtos;
        try {
            dtos = call(() -> http.get().uri(uri).retrieve().body(OrderDto[].class));
        } catch (HttpClientErrorException.NotFound ex) {
            // Colección vacía = 404 "Last page is 0", no un array vacío: una tienda sin ventas en la ventana.
            return List.of();
        }
        if (dtos == null) {
            return List.of();
        }
        List<Order> orders = new ArrayList<>(dtos.length);
        for (OrderDto d : dtos) {
            orders.add(toOrder(d));
        }
        return orders;
    }

    @Override
    public ProductPage listProducts(int page, int perPage) {
        Function<UriBuilder, URI> uri = b -> b.path("/{store}/products")
                .queryParam("page", page)
                .queryParam("per_page", perPage)
                .queryParam("fields", "id,name,variants")
                .build(props.storeId());
        ResponseEntity<ProductDto[]> res;
        try {
            res = call(() -> http.get().uri(uri).retrieve().toEntity(ProductDto[].class));
        } catch (HttpClientErrorException.NotFound ex) {
            // Una página más allá de la última responde 404 (no un array vacío): es fin de catálogo.
            return new ProductPage(List.of(), false);
        }
        ProductDto[] dtos = res.getBody();
        if (dtos == null) {
            return new ProductPage(List.of(), false);
        }
        List<Product> items = new ArrayList<>(dtos.length);
        for (ProductDto d : dtos) {
            items.add(toProduct(d));
        }
        return new ProductPage(items, tieneSiguiente(res.getHeaders().getFirst("Link")));
    }

    @Override
    public List<Webhook> listWebhooks() {
        WebhookDto[] dtos = call(() -> http.get()
                .uri("/{store}/webhooks", props.storeId())
                .retrieve()
                .body(WebhookDto[].class));
        if (dtos == null) {
            return List.of();
        }
        List<Webhook> webhooks = new ArrayList<>(dtos.length);
        for (WebhookDto d : dtos) {
            webhooks.add(new Webhook(d.id(), d.event(), d.url()));
        }
        return webhooks;
    }

    @Override
    public Webhook createWebhook(String event, String url) {
        WebhookDto dto = call(() -> http.post()
                .uri("/{store}/webhooks", props.storeId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("event", event, "url", url))
                .retrieve()
                .body(WebhookDto.class));
        return dto == null ? null : new Webhook(dto.id(), dto.event(), dto.url());
    }

    // --- ejecución con retry/backoff ---

    private <T> T call(Supplier<T> action) {
        int attempts = 0;
        while (true) {
            try {
                return action.get();
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (++attempts >= MAX_RETRIES) {
                    log.warn("[tiendanube] 429 tras {} intentos — abandonando", attempts);
                    throw new IntegrationUnavailableException("tiendanube");
                }
                backoff(ex);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
                // Token inválido o sin el scope pedido: es config, no un request mal armado → degradar.
                log.warn("[tiendanube] credenciales/scope insuficientes: {}", ex.getMessage());
                throw new IntegrationUnavailableException("tiendanube");
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                // 5xx o red caída: transitorio → degradar como el stub (cupón/evento quedan pendientes).
                log.warn("[tiendanube] no disponible: {}", ex.getMessage());
                throw new IntegrationUnavailableException("tiendanube");
            }
        }
    }

    private void backoff(HttpClientErrorException.TooManyRequests ex) {
        long waitMs = DEFAULT_BACKOFF_MS;
        String reset = ex.getResponseHeaders() != null
                ? ex.getResponseHeaders().getFirst("x-rate-limit-reset") : null;
        if (reset != null) {
            try {
                waitMs = Math.min(MAX_BACKOFF_MS, Math.max(0, Long.parseLong(reset.trim())));
            } catch (NumberFormatException ignored) {
                // header ausente/ilegible → backoff por defecto
            }
        }
        try {
            sleeper.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IntegrationUnavailableException("tiendanube");
        }
    }

    private static Order toOrder(OrderDto d) {
        if (d == null) {
            return null;
        }
        List<OrderCoupon> coupons = new ArrayList<>();
        if (d.coupon() != null) {
            for (CouponRefDto c : d.coupon()) {
                coupons.add(new OrderCoupon(c.id(), c.code()));
            }
        }
        return new Order(d.id(), d.number(), d.total(), d.paymentStatus(), parseInstant(d.paidAt()), coupons);
    }

    /** {@code paid_at} viene ISO-8601 con offset. Tolerante: null-safe y con fallback a Instant. */
    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (RuntimeException ex1) {
            try {
                return Instant.parse(raw);
            } catch (RuntimeException ex2) {
                log.debug("[tiendanube] paid_at no parseable: {}", raw);
                return null;
            }
        }
    }

    private static boolean tieneSiguiente(String linkHeader) {
        return linkHeader != null && linkHeader.contains("rel=\"next\"");
    }

    private static Product toProduct(ProductDto d) {
        List<Variant> variants = new ArrayList<>();
        if (d.variants() != null) {
            for (VariantDto v : d.variants()) {
                variants.add(new Variant(v.id(), v.sku()));
            }
        }
        return new Product(d.id(), nombreEs(d.name()), variants);
    }

    /** El nombre viene por idioma ({@code {"es": "..."}}); la tienda del cliente es sólo es-AR. */
    private static String nombreEs(Map<String, String> name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String es = name.get("es");
        return es != null ? es : name.values().iterator().next();
    }

    // --- DTOs de deserialización ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CouponDto(long id, String code, Boolean valid) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderDto(
            long id,
            Integer number,
            BigDecimal total,
            @JsonProperty("payment_status") String paymentStatus,
            @JsonProperty("paid_at") String paidAt,
            List<CouponRefDto> coupon) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CouponRefDto(Long id, String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductDto(long id, Map<String, String> name, List<VariantDto> variants) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VariantDto(long id, String sku) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WebhookDto(long id, String event, String url) {}
}
