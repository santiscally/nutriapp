package com.nutriapp.modules.dashboard.controller;

import com.nutriapp.modules.dashboard.dto.DashboardResumenResponse;
import com.nutriapp.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
