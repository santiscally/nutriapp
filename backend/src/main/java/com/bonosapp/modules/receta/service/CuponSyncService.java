package com.bonosapp.modules.receta.service;

import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.health.IntegrationHealthRegistry;
import com.bonosapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.receta.dto.ResyncCuponesResponse;
import com.bonosapp.modules.receta.entity.CuponSyncEstado;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro y reconciliación del cupón de TiendaNube de una receta (tarea 2.8). Un único lugar para
 * "intentar registrar el cupón", usado tanto en la emisión ({@link RecetaService#emitir}) como por
 * el resync manual/periódico de las que quedaron pendientes. En modo stub degrada sin romper: el
 * cupón queda PENDIENTE y se reintenta al pasar a live.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuponSyncService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Tope de recetas procesadas por corrida de resync (acota el I/O; el resto queda para la próxima). */
    private static final int RESYNC_BATCH = 100;

    private final RecetaRepository repo;
    private final ProductoRepository productoRepository;
    private final TiendaNubeClient tiendaNubeClient;
    private final IntegrationHealthRegistry health;

    /**
     * Intenta registrar el cupón de la receta en TiendaNube y setea su {@code cuponSyncEstado}
     * (NO persiste: el llamador guarda dentro de su transacción). En stub/caída degrada a PENDIENTE
     * sin propagar. Un error NO transitorio (ej. 4xx de la API) sí propaga: el llamador decide qué
     * hacer (la emisión lo deja subir; el resync lo captura y marca ERROR).
     */
    public void registrar(Receta receta) {
        List<Producto> productos = receta.getItems().stream()
                .map(i -> productoRepository.findById(i.getProductoId()).orElse(null))
                .toList();
        List<Long> productIds = productos.stream()
                .filter(p -> p != null && p.getTiendanubeProductId() != null)
                .map(Producto::getTiendanubeProductId)
                .toList();
        if (productIds.isEmpty() || productIds.size() != productos.size()) {
            // Sin el id de TODOS los productos el cupón saldría sin restringir: descuento a toda la tienda.
            String detalle = productos.stream()
                    .filter(p -> p == null || p.getTiendanubeProductId() == null)
                    .map(p -> p == null ? "(producto inexistente)" : p.getNombre())
                    .collect(Collectors.joining(", "));
            receta.setCuponSyncEstado(CuponSyncEstado.PENDIENTE);
            receta.setCuponSyncError(CuponSyncEstado.SIN_MAPEO + ": " + detalle);
            log.warn("Cupón de receta {} NO se registra: productos sin mapear ({}). "
                    + "Correr POST /admin/tiendanube/mapear-productos.", receta.getCodigo(), detalle);
            return;
        }
        try {
            TiendaNubeClient.Coupon coupon = tiendaNubeClient.createCoupon(new TiendaNubeClient.CouponRequest(
                    receta.getCodigo(),
                    receta.getDescuentoPct(),
                    LocalDate.now(AR),
                    receta.getVenceAt(),
                    productIds));
            receta.setCuponTiendanubeId(coupon.id());
            receta.setCuponSyncEstado(CuponSyncEstado.SINCRONIZADO);
            receta.setCuponSyncError(null);
            health.registrarExito(Proveedor.TIENDANUBE);
        } catch (IntegrationUnavailableException ex) {
            receta.setCuponSyncEstado(CuponSyncEstado.PENDIENTE);
            receta.setCuponSyncError(ex.getMessage());
            health.registrarError(Proveedor.TIENDANUBE, ex.getMessage());
            log.info("Cupón de receta {} queda PENDIENTE de sync: {}", receta.getCodigo(), ex.getMessage());
        }
    }

    /**
     * Reintenta el registro del cupón de todas las recetas PENDIENTES cuyo cupón no sincronizó
     * (hasta {@value #RESYNC_BATCH} por corrida). En stub siguen quedando pendientes con mensaje
     * explícito; en live drena lo acumulado. Idempotente: una receta ya sincronizada no vuelve a entrar.
     */
    @Transactional
    public ResyncCuponesResponse resync() {
        List<Receta> pendientes = repo.findResyncables(PageRequest.of(0, RESYNC_BATCH));
        int intentados = 0;
        int sincronizados = 0;
        for (Receta receta : pendientes) {
            intentados++;
            try {
                registrar(receta);
                if (receta.getCuponSyncEstado() == CuponSyncEstado.SINCRONIZADO) {
                    sincronizados++;
                }
            } catch (RuntimeException hard) {
                // No transitorio (no es stub/caída): marcamos ERROR y seguimos con las demás.
                receta.setCuponSyncEstado(CuponSyncEstado.ERROR);
                receta.setCuponSyncError(hard.getMessage());
                health.registrarError(Proveedor.TIENDANUBE, hard.getMessage());
                log.warn("Resync de cupón de receta {} falló (no transitorio): {}",
                        receta.getCodigo(), hard.getMessage());
            }
            repo.save(receta);
        }
        long pendientesRestantes = repo.countResyncables();
        if (intentados > 0) {
            log.info("[cupon-resync] intentados={} sincronizados={} pendientes-restantes={}",
                    intentados, sincronizados, pendientesRestantes);
        }
        return new ResyncCuponesResponse(intentados, sincronizados, pendientesRestantes);
    }
}
