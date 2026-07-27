package com.nutriapp.modules.dashboard.controller;

import com.nutriapp.modules.dashboard.dto.CierreMensualResponse;
import com.nutriapp.modules.dashboard.dto.DashboardResumenResponse;
import com.nutriapp.modules.dashboard.dto.EstadisticasResponse;
import com.nutriapp.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public DashboardResumenResponse resumen() {
        return service.resumen();
    }

    @GetMapping("/cierre-mensual")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public CierreMensualResponse cierreMensual(
            @RequestParam int year,
            @RequestParam int month) {
        return service.cierreMensual(year, month);
    }

    /** Serie mensual (últimos {@code meses}, cronológica) para los gráficos del dashboard. */
    @GetMapping("/estadisticas")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public EstadisticasResponse estadisticas(@RequestParam(defaultValue = "6") int meses) {
        return service.estadisticas(meses);
    }
}
