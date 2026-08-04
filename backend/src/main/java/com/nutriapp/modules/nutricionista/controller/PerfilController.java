package com.nutriapp.modules.nutricionista.controller;

import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.entity.TipoArchivo;
import com.nutriapp.modules.nutricionista.service.ArchivoService;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * C-17 — foto de perfil de la nutricionista. Cada una maneja la suya: no hay id en la ruta, sale
 * del token. La foto se devuelve embebida en {@code GET /me} como data URI (es un thumbnail de
 * pocos KB), así el front pinta el avatar sin un segundo request autenticado.
 */
@RestController
@RequestMapping("/api/v1/perfil/foto")
@RequiredArgsConstructor
public class PerfilController {

    private final NutricionistaService nutricionistaService;
    private final ArchivoService archivoService;

    /** Sube o reemplaza la foto. Se guarda redimensionada; devuelve el data URI ya listo. */
    @PostMapping
    public ResponseEntity<Map<String, String>> subir(@RequestPart("foto") MultipartFile foto) {
        Nutricionista yo = nutricionistaService.getCurrent();
        archivoService.guardar(yo.getId(), TipoArchivo.FOTO_PERFIL, foto);
        return ResponseEntity.ok(Map.of("foto", archivoService.fotoDataUri(yo.getId())));
    }

    @DeleteMapping
    public ResponseEntity<Void> borrar() {
        archivoService.borrar(nutricionistaService.getCurrent().getId(), TipoArchivo.FOTO_PERFIL);
        return ResponseEntity.noContent().build();
    }
}
