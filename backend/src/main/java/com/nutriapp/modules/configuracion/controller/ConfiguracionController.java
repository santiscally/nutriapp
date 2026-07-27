package com.nutriapp.modules.configuracion.controller;

import com.nutriapp.modules.configuracion.dto.ConfiguracionResponse;
import com.nutriapp.modules.configuracion.dto.ConfiguracionUpdateRequest;
import com.nutriapp.modules.configuracion.service.ConfiguracionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService service;

    /** Parámetros vigentes. Cualquier autenticado los lee (el emisor necesita el descuento). */
    @GetMapping("/configuracion")
    @PreAuthorize("isAuthenticated()")
    public ConfiguracionResponse get() {
        return service.get();
    }

    /** Actualiza los parámetros. Solo admin. */
    @PutMapping("/admin/configuracion")
    @PreAuthorize("hasAuthority('admin:manage')")
    public ConfiguracionResponse actualizar(@Valid @RequestBody ConfiguracionUpdateRequest req) {
        return service.actualizar(req);
    }
}
