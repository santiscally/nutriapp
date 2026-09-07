package com.bonosapp.modules.paciente.controller;

import com.bonosapp.common.dto.PageResponse;
import com.bonosapp.modules.paciente.dto.PacienteCreateRequest;
import com.bonosapp.modules.paciente.dto.PacienteResponse;
import com.bonosapp.modules.paciente.dto.PacienteUpdateRequest;
import com.bonosapp.modules.paciente.service.PacienteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService service;

    @GetMapping
    @PreAuthorize("hasAuthority('pacientes:read')")
    public PageResponse<PacienteResponse> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.search(q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pacientes:read')")
    public PacienteResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pacientes:write')")
    public ResponseEntity<PacienteResponse> create(@Valid @RequestBody PacienteCreateRequest req) {
        PacienteResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/pacientes/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pacientes:write')")
    public PacienteResponse update(@PathVariable UUID id, @Valid @RequestBody PacienteUpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('pacientes:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
