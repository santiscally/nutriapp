package com.nutriapp.integrations.tiendanube;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Port de TiendaNube. Contrato calcado de la API 2025-03
 * (ver instrucciones_claude/03-integraciones-apis.md §2).
 * Impl real (Fase 1.6/2): HttpTiendaNubeClient. Stub: StubTiendaNubeClient.
 */
public interface TiendaNubeClient {

    /** POST /{store_id}/coupons — cupón único por receta (max_uses=1, restringido a productos). */
    Coupon createCoupon(CouponRequest request);

    /** DELETE /{store_id}/coupons/{id} — al anular una receta. */
    void deleteCoupon(long couponId);

    record CouponRequest(
            String code,
            BigDecimal valuePct,
            LocalDate startDate,
            LocalDate endDate,
            List<Long> productIds
    ) {}

    record Coupon(long id, String code, boolean valid) {}
}
