package com.nutriapp.integrations.tiendanube;

import com.nutriapp.integrations.IntegrationUnavailableException;
import lombok.extern.slf4j.Slf4j;

/**
 * Modo stub: no hay conexión. La emisión de recetas captura la excepción y deja
 * el cupón en PENDIENTE de sync; el CuponSyncJob (Fase 1) lo drena al pasar a live.
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
}
