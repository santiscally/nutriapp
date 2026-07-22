package com.nutriapp.modules.admin.controller;

import com.nutriapp.common.dto.PageResponse;
import com.nutriapp.modules.admin.dto.NutricionistaResponse;
import com.nutriapp.modules.admin.dto.RechazarRequest;
import com.nutriapp.modules.admin.service.AdminNutricionistaService;
import com.nutriapp.modules.nutricionista.entity.EstadoValidacion;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja de validación de nutricionistas — sólo ADMIN (authority admin:manage). */
@RestController
@RequestMapping("/api/v1/admin/nutricionistas")
@RequiredArgsConstructor
public class AdminNutricionistaController {

    private final AdminNutricionistaService service;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:manage')")
    public PageResponse<NutricionistaResponse> listar(
            @RequestParam(required = false) EstadoValidacion estado,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.listar(estado, q, pageable));
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('admin:manage')")
    public NutricionistaResponse aprobar(@PathVariable UUID id) {
        return service.aprobar(id);
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('admin:manage')")
    public NutricionistaResponse rechazar(@PathVariable UUID id, @RequestBody(required = false) RechazarRequest req) {
        return service.rechazar(id, req != null ? req.motivo() : null);
    }
}
