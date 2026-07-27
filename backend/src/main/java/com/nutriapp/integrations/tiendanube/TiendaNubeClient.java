package com.nutriapp.integrations.tiendanube;

import java.math.BigDecimal;
import java.time.Instant;
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

    /** GET /{store_id}/orders/{id} — tras un webhook order/paid: leer cupón + total + estado de pago. */
    Order getOrder(long orderId);

    /**
     * GET /{store_id}/orders?payment_status=paid&updated_at_min=... — polling de respaldo.
     * No se puede filtrar por cupón server-side: se matchea en memoria contra nuestros códigos.
     */
    List<Order> getPaidOrdersSince(Instant since);

    record CouponRequest(
            String code,
            BigDecimal valuePct,
            LocalDate startDate,
            LocalDate endDate,
            List<Long> productIds
    ) {}

    record Coupon(long id, String code, boolean valid) {}

    /**
     * Subset del objeto order que usamos para detectar la conversión.
     * {@code paymentStatus == "paid"} indica orden pagada (ver isPaid()).
     */
    record Order(
            long id,
            Integer number,
            BigDecimal total,
            String paymentStatus,
            Instant paidAt,
            List<OrderCoupon> coupons
    ) {
        public boolean isPaid() {
            return "paid".equalsIgnoreCase(paymentStatus);
        }
    }

    /** Cupón asociado a una orden. {@code code} matchea recetas.codigo. */
    record OrderCoupon(Long id, String code) {}
}
