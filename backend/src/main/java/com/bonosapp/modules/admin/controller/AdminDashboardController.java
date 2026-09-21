package com.bonosapp.modules.admin.controller;

import com.bonosapp.modules.admin.dto.AdminDashboardResumenResponse;
import com.bonosapp.modules.admin.dto.AdminEstadisticasResponse;
import com.bonosapp.modules.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S-13 — solapa PANEL del admin: el dashboard de la profesional, consolidado sobre todas. */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService service;

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('admin:manage')")
    public AdminDashboardResumenResponse resumen() {
        return service.resumen();
    }

    @GetMapping("/estadisticas")
    @PreAuthorize("hasAuthority('admin:manage')")
    public AdminEstadisticasResponse estadisticas(@RequestParam(defaultValue = "6") int meses) {
        return service.estadisticas(meses);
    }
}
