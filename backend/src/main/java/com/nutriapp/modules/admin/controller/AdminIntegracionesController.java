package com.nutriapp.modules.admin.controller;

import com.nutriapp.modules.admin.dto.IntegracionesEstadoResponse;
import com.nutriapp.modules.admin.service.IntegracionesEstadoService;
import com.nutriapp.modules.producto.dto.SyncProductosResponse;
import com.nutriapp.modules.producto.service.ProductoSyncService;
import com.nutriapp.modules.receta.dto.ResyncCuponesResponse;
import com.nutriapp.modules.receta.service.CuponSyncService;
import lombok.RequiredArgsConstructor;
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

    /** 2.7 — Estado por proveedor: modo, disponible, pendientes, último error, última sync. */
    @GetMapping("/integraciones/estado")
    @PreAuthorize("hasAuthority('admin:manage')")
    public IntegracionesEstadoResponse estado() {
        return integracionesEstadoService.estado();
    }

    /** 2.8 — Reintenta el registro de los cupones que quedaron pendientes de sync. */
    @PostMapping("/tiendanube/resync-cupones")
    @PreAuthorize("hasAuthority('admin:manage')")
    public ResyncCuponesResponse resyncCupones() {
        return cuponSyncService.resync();
    }

    /** 2.9 — Fuerza la sync del catálogo desde Contabilium. En stub → 503 "Contabilium no conectada". */
    @PostMapping("/contabilium/sync-productos")
    @PreAuthorize("hasAuthority('admin:manage')")
    public SyncProductosResponse syncProductos() {
        return productoSyncService.sync();
    }
}
