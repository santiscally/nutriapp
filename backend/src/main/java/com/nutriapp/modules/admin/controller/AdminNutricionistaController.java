package com.nutriapp.modules.admin.controller;

import com.nutriapp.common.dto.PageResponse;
import com.nutriapp.modules.admin.dto.NutricionistaResponse;
import com.nutriapp.modules.admin.dto.ParametrosNutricionistaRequest;
import com.nutriapp.modules.admin.dto.RechazarRequest;
import com.nutriapp.modules.admin.service.AdminNutricionistaService;
import com.nutriapp.modules.nutricionista.entity.NutricionistaArchivo;
import com.nutriapp.modules.nutricionista.entity.TipoArchivo;
import com.nutriapp.modules.nutricionista.service.ArchivoService;
import com.nutriapp.modules.nutricionista.entity.EstadoValidacion;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ArchivoService archivoService;

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

    /**
     * C-01 — % de descuento y de comisión propios de esta nutricionista.
     * Mandar un campo en null lo devuelve al valor global. Sólo el admin toca esto (call 19:29).
     */
    @PutMapping("/{id}/parametros")
    @PreAuthorize("hasAuthority('admin:manage')")
    public NutricionistaResponse actualizarParametros(@PathVariable UUID id,
                                                      @Valid @RequestBody ParametrosNutricionistaRequest req) {
        return service.actualizarParametros(id, req.descuentoPct(), req.comisionPct());
    }

    /**
     * C-08 — descarga la matrícula/título que subió al registrarse, para poder validarla.
     * {@code inline} para que el PDF se abra en el visor del browser en vez de bajarse.
     */
    @GetMapping("/{id}/matricula")
    @PreAuthorize("hasAuthority('admin:manage')")
    public ResponseEntity<byte[]> matricula(@PathVariable UUID id) {
        NutricionistaArchivo a = archivoService.obtener(id, TipoArchivo.MATRICULA);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(a.getNombreOriginal() != null ? a.getNombreOriginal() : "matricula")
                                .build().toString())
                .body(a.getContenido());
    }
}
