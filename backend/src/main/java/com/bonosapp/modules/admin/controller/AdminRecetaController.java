package com.bonosapp.modules.admin.controller;

import com.bonosapp.common.dto.PageResponse;
import com.bonosapp.modules.admin.dto.AdminRecetaResponse;
import com.bonosapp.modules.admin.service.AdminRecetaService;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S-14 — solapa BONOS del admin: los bonos de todas, con el filtro extra por profesional. */
@RestController
@RequestMapping("/api/v1/admin/recetas")
@RequiredArgsConstructor
public class AdminRecetaController {

    private final AdminRecetaService service;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:manage')")
    public PageResponse<AdminRecetaResponse> list(
            @RequestParam(required = false) EstadoReceta estado,
            @RequestParam(required = false) UUID nutricionistaId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.search(estado, nutricionistaId, q, desde, hasta, pageable));
    }
}
