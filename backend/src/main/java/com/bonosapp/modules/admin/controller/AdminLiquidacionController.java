package com.bonosapp.modules.admin.controller;

import com.bonosapp.modules.admin.dto.CierreConsolidadoResponse;
import com.bonosapp.modules.admin.dto.LiquidacionResponse;
import com.bonosapp.modules.admin.dto.LiquidarRecetasRequest;
import com.bonosapp.modules.admin.service.CierreConsolidadoService;
import com.bonosapp.modules.admin.service.LiquidacionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C-05 — liquidación de comisiones. Sólo ADMIN ({@code admin:manage}): la nutricionista nunca
 * marca sus propias recetas como pagadas (call 57:43).
 *
 * <p>El cierre consolidado del admin que consume esto es C-06, todavía sin hacer.
 */
@RestController
@RequestMapping("/api/v1/admin/liquidaciones")
@RequiredArgsConstructor
public class AdminLiquidacionController {

    private final LiquidacionService liquidacionService;
    private final CierreConsolidadoService cierreConsolidadoService;

    /** Marca como LIQUIDADAS las recetas indicadas. Idempotente: lo ya liquidado se omite. */
    @PostMapping
    @PreAuthorize("hasAuthority('admin:manage')")
    public LiquidacionResponse liquidar(@Valid @RequestBody LiquidarRecetasRequest req) {
        return liquidacionService.liquidar(req.recetaIds());
    }

    /**
     * C-06 — consolidado de todas las nutricionistas en un rango (fechas inclusive, hora AR).
     * Sin paginar: el exportable tiene que salir completo.
     */
    @GetMapping("/consolidado")
    @PreAuthorize("hasAuthority('admin:manage')")
    public CierreConsolidadoResponse consolidado(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return cierreConsolidadoService.consolidado(desde, hasta);
    }
}
