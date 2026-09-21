package com.bonosapp.modules.bonopdf.controller;

import com.bonosapp.modules.bonopdf.service.BonoPdfService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Descarga del PDF del bono (F-21; alimenta el ícono de re-descarga del front, F-15).
 *
 * <p>Cuelga de {@code /recetas/{id}/pdf} para no inventar una raíz nueva, pero vive en su propio
 * controller —y no dentro de {@code RecetaController}— porque el PDF es del vertical de
 * notificaciones (Fran) y las recetas son del otro (Santi): así los dos lados se tocan sin
 * pisarse el mismo archivo.
 *
 * <p>Permiso {@code recetas:read}: es el mismo bono que ya puede ver, en otro formato. La
 * pertenencia la valida el service (404 si el bono no es suyo).
 */
@RestController
@RequestMapping("/api/v1/recetas")
@RequiredArgsConstructor
public class BonoPdfController {

    private final BonoPdfService service;

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('recetas:read')")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID id) {
        BonoPdfService.Bono bono = service.generar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // attachment + filename: el navegador lo baja con nombre propio en vez de abrir
                // un blob sin nombre en una pestaña.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(bono.nombreArchivo()).build().toString())
                .body(bono.contenido());
    }
}
