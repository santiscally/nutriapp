package com.nutriapp.modules.admin.controller;

import com.nutriapp.modules.admin.dto.IntegracionesEstadoResponse;
import com.nutriapp.modules.admin.service.IntegracionesEstadoService;
import com.nutriapp.modules.producto.dto.MapeoTiendaNubeResponse;
import com.nutriapp.modules.producto.service.ProductoSyncService;
import com.nutriapp.modules.producto.service.TiendaNubeMapeoService;
import com.nutriapp.modules.receta.dto.ResyncCuponesResponse;
import com.nutriapp.modules.webhook.dto.RegistrarWebhooksResponse;
import com.nutriapp.modules.webhook.service.TiendaNubeWebhookRegistrar;
import com.nutriapp.modules.receta.service.CuponSyncService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones de resiliencia sobre las integraciones externas — sólo ADMIN ({@code admin:manage}).
 * Visibilidad (2.7) + acciones manuales de recuperación de lo pendiente (2.8 cupones, 2.9 productos).
 * Todo degrada explícitamente en stub y se enciende al pasar los proveedores a live (Fase 2).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminIntegracionesController {

    private final IntegracionesEstadoService integracionesEstadoService;
    private final CuponSyncService cuponSyncService;
    private final ProductoSyncService productoSyncService;
    private final TiendaNubeMapeoService tiendaNubeMapeoService;
    private final TiendaNubeWebhookRegistrar tiendaNubeWebhookRegistrar;

    /** 2.7 — Estado por proveedor: modo, disponible, pendientes, último error, última sync. */
    @GetMapping("/integraciones/estado")
    @PreAuthorize("hasAuthority('admin:manage')")
    public IntegracionesEstadoResponse estado() {
        return integracionesEstadoService.estado();
    }

    /**
     * Concilia el catálogo local con la tienda por SKU y guarda los ids de TiendaNube. Hay que
     * correrlo antes de emitir recetas en live: sin esos ids el cupón sale sin restricción de
     * productos. Síncrono — son ~12 requests y el admin necesita ver qué SKUs no matchearon.
     */
    @PostMapping("/tiendanube/mapear-productos")
    @PreAuthorize("hasAuthority('admin:manage')")
    public MapeoTiendaNubeResponse mapearProductos() {
        return tiendaNubeMapeoService.mapear();
    }

    /**
     * Suscribe la app a {@code order/paid} en la tienda configurada. Paso de puesta en marcha, una
     * vez por tienda: sin esto no llega ningún webhook y la conversión sólo la detecta el polling.
     * Idempotente. Exige {@code APP_PUBLIC_URL} en HTTPS.
     */
    @PostMapping("/tiendanube/registrar-webhooks")
    @PreAuthorize("hasAuthority('admin:manage')")
    public RegistrarWebhooksResponse registrarWebhooks() {
        return tiendaNubeWebhookRegistrar.registrar();
    }

    /** 2.8 — Reintenta el registro de los cupones que quedaron pendientes de sync. */
    @PostMapping("/tiendanube/resync-cupones")
    @PreAuthorize("hasAuthority('admin:manage')")
    public ResyncCuponesResponse resyncCupones() {
        return cuponSyncService.resync();
    }

    /**
     * 2.9 — Dispara la sync del catálogo desde Contabilium. **Asíncrono**: responde 202 al toque y
     * corre en background (~2266 productos, puede tardar hasta un minuto). El progreso/resultado se
     * ve en {@code GET /integraciones/estado} (campos {@code sincronizando} + {@code ultimoResultado}).
     * En stub la sync interna degrada con 503 y el resultado queda como "error: ...".
     */
    @PostMapping("/contabilium/sync-productos")
    @PreAuthorize("hasAuthority('admin:manage')")
    public ResponseEntity<Map<String, Object>> syncProductos() {
        boolean yaCorria = productoSyncService.isSincronizando();
        productoSyncService.syncAsync();
        return ResponseEntity.accepted().body(Map.of(
                "estado", yaCorria ? "en_curso" : "iniciada",
                "mensaje", yaCorria
                        ? "Ya hay una sincronización en curso."
                        : "Sincronización iniciada; puede tardar hasta un minuto. Seguí el progreso en el estado de integraciones."));
    }
}
