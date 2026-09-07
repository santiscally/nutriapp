package com.nutriapp.integrations.tiendanube;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.IntegrationsProperties;
import com.nutriapp.integrations.support.Sleeper;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient.Coupon;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient.CouponRequest;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient.Order;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient.ProductPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpTiendaNubeClientTest {

    private static final String UA = "BonosApp (contacto@x.com)";
    private WireMockServer wm;

    @BeforeEach
    void up() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void down() {
        wm.stop();
    }

    /** Sleeper no-op: el backoff de 429 no bloquea el test. */
    private HttpTiendaNubeClient client() {
        Sleeper noop = millis -> { };
        return new HttpTiendaNubeClient(new IntegrationsProperties.TiendaNube(
                "live", "http://localhost:" + wm.port(), "STORE1", "TOKEN1",
                "cid", "csecret", UA, "whsecret", "https://tienda.test"), noop);
    }

    @Test
    void createCoupon_mandaHeadersYBodyCorrectos() {
        wm.stubFor(post(urlEqualTo("/STORE1/coupons")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":999,\"code\":\"RX-ABC\",\"valid\":true}")));

        Coupon c = client().createCoupon(new CouponRequest(
                "RX-ABC", new BigDecimal("30"), LocalDate.of(2026, 7, 17), LocalDate.of(2026, 8, 16),
                List.of(1234L, 5678L)));

        assertThat(c.id()).isEqualTo(999);
        assertThat(c.code()).isEqualTo("RX-ABC");
        assertThat(c.valid()).isTrue();
        wm.verify(postRequestedFor(urlEqualTo("/STORE1/coupons"))
                .withHeader("User-Agent", equalTo(UA))
                .withHeader("Authorization", equalTo("Bearer TOKEN1"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("percentage")))
                .withRequestBody(matchingJsonPath("$.value", equalTo("30.00")))
                .withRequestBody(matchingJsonPath("$.max_uses", equalTo("1")))
                .withRequestBody(matchingJsonPath("$.code", equalTo("RX-ABC")))
                .withRequestBody(matchingJsonPath("$.products")));
    }

    @Test
    void deleteCoupon_haceDelete() {
        wm.stubFor(delete(urlEqualTo("/STORE1/coupons/999"))
                .willReturn(aResponse().withStatus(200)));

        client().deleteCoupon(999L);

        wm.verify(deleteRequestedFor(urlEqualTo("/STORE1/coupons/999"))
                .withHeader("User-Agent", equalTo(UA)));
    }

    @Test
    void getOrder_mapeaCuponTotalYEstado() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/orders/555")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "id": 555,
                          "number": 306,
                          "total": "1000.00",
                          "payment_status": "paid",
                          "paid_at": "2026-07-20T12:00:00-03:00",
                          "coupon": [{"id": 9, "code": "RX-ABC"}]
                        }
                        """)));

        Order o = client().getOrder(555L);

        assertThat(o.isPaid()).isTrue();
        assertThat(o.total()).isEqualByComparingTo("1000.00");
        assertThat(o.number()).isEqualTo(306);
        assertThat(o.paidAt()).isNotNull();
        assertThat(o.coupons()).hasSize(1);
        assertThat(o.coupons().get(0).code()).isEqualTo("RX-ABC");
    }

    @Test
    void getPaidOrdersSince_parseaLista() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/orders")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        [
                          {"id": 1, "total": "500.00", "payment_status": "paid",
                           "coupon": [{"id": 2, "code": "RX-1"}]}
                        ]
                        """)));

        List<Order> orders = client().getPaidOrdersSince(Instant.parse("2026-07-20T00:00:00Z"));

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).coupons().get(0).code()).isEqualTo("RX-1");
        wm.verify(getRequestedFor(urlPathEqualTo("/STORE1/orders"))
                .withQueryParam("payment_status", equalTo("paid"))
                .withQueryParam("updated_at_min", matching(".+")));
    }

    @Test
    void ante429_haceBackoffYReintenta() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/orders/1"))
                .inScenario("rate-limit")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("x-rate-limit-reset", "0"))
                .willSetStateTo("ok"));
        wm.stubFor(get(urlPathEqualTo("/STORE1/orders/1"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"total\":\"10.00\",\"payment_status\":\"paid\"}")));

        Order o = client().getOrder(1L);

        assertThat(o.isPaid()).isTrue();
        wm.verify(2, getRequestedFor(urlPathEqualTo("/STORE1/orders/1")));
    }

    @Test
    void ante5xx_degradaAIntegrationUnavailable() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/orders/2"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client().getOrder(2L))
                .isInstanceOf(IntegrationUnavailableException.class);
    }

    @Test
    void listProducts_parseaSkusYDetectaSiguientePagina() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/products")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withHeader("Link", "<http://x/products?page=2>; rel=\"next\", <http://x/products?page=3>; rel=\"last\"")
                .withBody("""
                        [{"id": 363154002, "name": {"es": "ON-ROLL FEM X 60G"},
                          "variants": [{"id": 1583970721, "sku": "119"}]}]
                        """)));

        ProductPage page = client().listProducts(1, 200);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(363154002L);
        assertThat(page.items().get(0).name()).isEqualTo("ON-ROLL FEM X 60G");
        assertThat(page.items().get(0).variants().get(0).id()).isEqualTo(1583970721L);
        assertThat(page.items().get(0).variants().get(0).sku()).isEqualTo("119");
        wm.verify(getRequestedFor(urlPathEqualTo("/STORE1/products"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("per_page", equalTo("200"))
                .withHeader("User-Agent", equalTo(UA)));
    }

    @Test
    void listProducts_sinLinkNextEsUltimaPagina() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/products")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withHeader("Link", "<http://x/products?page=1>; rel=\"prev\"")
                .withBody("[]")));

        assertThat(client().listProducts(3, 200).hasNext()).isFalse();
    }

    @Test
    void listProducts_paginaMasAllaDeLaUltimaDa404YSeTrataComoFin() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/products")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"description\": \"Last page is 3\"}")));

        ProductPage page = client().listProducts(9, 200);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void listProducts_variantSinSkuLlegaComoNull() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/products")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        [{"id": 1, "name": {"es": "Sin sku"}, "variants": [{"id": 2, "sku": null}]}]
                        """)));

        assertThat(client().listProducts(1, 200).items().get(0).variants().get(0).sku()).isNull();
    }

    @Test
    void un403PorScopeFaltanteDegradaComoNoDisponible() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/coupons")).willReturn(aResponse().withStatus(403)));
        wm.stubFor(post(urlPathEqualTo("/STORE1/coupons")).willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> client().createCoupon(new CouponRequest(
                "RX-1", new BigDecimal("30"), LocalDate.now(), LocalDate.now().plusDays(30), List.of(1L))))
                .isInstanceOf(IntegrationUnavailableException.class);
    }

    @Test
    void un401PorTokenInvalidoDegradaComoNoDisponible() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/orders/7")).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client().getOrder(7L))
                .isInstanceOf(IntegrationUnavailableException.class);
    }

    @Test
    void createCoupon_sinProductosSeNiegaAEmitirUnCuponParaTodaLaTienda() {
        assertThatThrownBy(() -> client().createCoupon(new CouponRequest(
                "RX-VACIO", new BigDecimal("30"), LocalDate.now(), LocalDate.now().plusDays(30), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toda la tienda");
    }

    @Test
    void listWebhooks_devuelveLoRegistrado() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/webhooks")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        [{"id": 55, "event": "order/paid", "url": "https://bonosapp.com.ar/api/v1/webhooks/tiendanube"}]
                        """)));

        assertThat(client().listWebhooks()).singleElement().satisfies(w -> {
            assertThat(w.id()).isEqualTo(55L);
            assertThat(w.event()).isEqualTo("order/paid");
        });
    }

    @Test
    void createWebhook_mandaEventoYUrl() {
        wm.stubFor(post(urlPathEqualTo("/STORE1/webhooks")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": 77, "event": "order/paid", "url": "https://bonosapp.com.ar/api/v1/webhooks/tiendanube"}
                        """)));

        assertThat(client().createWebhook("order/paid", "https://bonosapp.com.ar/api/v1/webhooks/tiendanube").id())
                .isEqualTo(77L);
        wm.verify(postRequestedFor(urlPathEqualTo("/STORE1/webhooks"))
                .withRequestBody(matchingJsonPath("$.event", equalTo("order/paid")))
                .withRequestBody(matchingJsonPath("$.url",
                        equalTo("https://bonosapp.com.ar/api/v1/webhooks/tiendanube"))));
    }

    @Test
    void getPaidOrdersSince_sinOrdenesDa404YDevuelveListaVacia() {
        wm.stubFor(get(urlPathEqualTo("/STORE1/orders")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"description\": \"Last page is 0\"}")));

        assertThat(client().getPaidOrdersSince(Instant.now())).isEmpty();
    }
}
