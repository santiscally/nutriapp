package com.bonosapp.integrations.tiendanube;

import com.bonosapp.integrations.IntegrationUnavailableException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Modo stub: no hay conexión. La emisión de recetas captura la excepción y deja
 * el cupón en PENDIENTE de sync; el CuponSyncJob (Fase 1) lo drena al pasar a live.
 * En la ruta de webhook/polling, {@code getOrder}/{@code getPaidOrdersSince} degradan igual:
 * el evento queda sin procesar y se reintenta al pasar a live (ver TiendaNubeWebhookService).
 */
@Slf4j
public class StubTiendaNubeClient implements TiendaNubeClient {

    @Override
    public Coupon createCoupon(CouponRequest request) {
        log.info("[stub-tiendanube] createCoupon {} — sin conexión, queda pendiente de sync", request.code());
        throw new IntegrationUnavailableException("tiendanube");
    }

    @Override
    public void deleteCoupon(long couponId) {
        log.info("[stub-tiendanube] deleteCoupon {} — sin conexión", couponId);
        throw new IntegrationUnavailableException("tiendanube");
    }

    @Override
    public Order getOrder(long orderId) {
        log.info("[stub-tiendanube] getOrder {} — sin conexión", orderId);
        throw new IntegrationUnavailableException("tiendanube");
    }

    @Override
    public List<Order> getPaidOrdersSince(Instant since) {
        log.debug("[stub-tiendanube] getPaidOrdersSince {} — sin conexión", since);
        throw new IntegrationUnavailableException("tiendanube");
    }

    @Override
    public ProductPage listProducts(int page, int perPage) {
        log.debug("[stub-tiendanube] listProducts page={} — sin conexión", page);
        throw new IntegrationUnavailableException("tiendanube");
    }

    @Override
    public List<Webhook> listWebhooks() {
        log.debug("[stub-tiendanube] listWebhooks — sin conexión");
        throw new IntegrationUnavailableException("tiendanube");
    }

    @Override
    public Webhook createWebhook(String event, String url) {
        log.info("[stub-tiendanube] createWebhook {} {} — sin conexión", event, url);
        throw new IntegrationUnavailableException("tiendanube");
    }
}
