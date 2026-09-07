package com.bonosapp.modules.admin.controller;

import com.bonosapp.common.dto.PageResponse;
import com.bonosapp.modules.admin.dto.NutricionistaResponse;
import com.bonosapp.modules.admin.dto.ParametrosNutricionistaRequest;
import com.bonosapp.modules.admin.dto.RechazarRequest;
import com.bonosapp.modules.admin.dto.ResetPasswordRequest;
import com.bonosapp.modules.admin.service.AdminNutricionistaService;
import com.bonosapp.modules.nutricionista.entity.NutricionistaArchivo;
import com.bonosapp.modules.nutricionista.entity.TipoArchivo;
import com.bonosapp.modules.nutricionista.service.ArchivoService;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
     * Le quita el acceso sin borrar nada, y se puede revertir. Es lo que corresponde para alguien
     * que dejó de trabajar: rechazar reescribiría el historial de validación de su solicitud.
     */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasAuthority('admin:manage')")
    public NutricionistaResponse desactivar(@PathVariable UUID id) {
        return service.desactivar(id);
    }

    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasAuthority('admin:manage')")
    public NutricionistaResponse reactivar(@PathVariable UUID id) {
        return service.reactivar(id);
    }

    /**
     * Baja definitiva (usuario de Keycloak + fila local + pacientes). Para limpiar altas
     * equivocadas o de prueba: si emitió recetas responde 409 y hay que desactivarla, porque esas
     * recetas están en los cierres.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        service.eliminar(id);
    }

    /**
     * Le pone una contraseña nueva. Única vía de recuperación del sistema: no hay flujo de
     * "olvidé mi contraseña" por email.
     */
    @PostMapping("/{id}/password")
    @PreAuthorize("hasAuthority('admin:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetearPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest req) {
        service.resetearPassword(id, req.password());
    }

    /** % de descuento y de comisión de esta nutricionista. Ambos obligatorios (V011). */
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
