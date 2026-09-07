package com.bonosapp.modules.receta.controller;

import com.bonosapp.common.dto.PageResponse;
import com.bonosapp.modules.receta.dto.RecetaCreateRequest;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.service.RecetaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recetas")
@RequiredArgsConstructor
public class RecetaController {

    private final RecetaService service;

    @GetMapping
    @PreAuthorize("hasAuthority('recetas:read')")
    public PageResponse<RecetaResponse> list(
            @RequestParam(required = false) EstadoReceta estado,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.search(estado, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('recetas:read')")
    public RecetaResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('recetas:write')")
    public ResponseEntity<RecetaResponse> emitir(@Valid @RequestBody RecetaCreateRequest req) {
        RecetaResponse created = service.emitir(req);
        return ResponseEntity.created(URI.create("/api/v1/recetas/" + created.id())).body(created);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('recetas:write')")
    public RecetaResponse anular(@PathVariable UUID id) {
        return service.anular(id);
    }

    @PostMapping("/{id}/reenviar")
    @PreAuthorize("hasAuthority('recetas:write')")
    public RecetaResponse reenviar(@PathVariable UUID id) {
        return service.reenviar(id);
    }
}
