package com.nutriapp.modules.nutricionista.controller;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.integrations.keycloak.KeycloakAdminClient;
import com.nutriapp.modules.nutricionista.dto.CambiarPasswordRequest;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.entity.TipoArchivo;
import com.nutriapp.modules.nutricionista.service.ArchivoService;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Perfil propio: lo único que cada quien puede cambiar de sí mismo es su <b>foto</b> y su
 * <b>contraseña</b>. El resto de sus datos (nombre, matrícula, datos fiscales, porcentajes) los
 * toca el admin — son justamente los que se validaron al aprobarla.
 *
 * <p>No hay id en la ruta: sale del token.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final NutricionistaService nutricionistaService;
    private final ArchivoService archivoService;
    private final KeycloakAdminClient keycloak;

    /**
     * Cambia la propia contraseña. Exige la actual y la verifica contra Keycloak: la Admin API
     * puede pisar una credencial sin conocer la anterior, así que si no lo pidiéramos, una sesión
     * abierta y olvidada alcanzaría para quedarse con la cuenta.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest req) {
        Nutricionista yo = nutricionistaService.getCurrent();
        if (yo.getKeycloakUserId() == null) {
            throw new ConflictException("Tu usuario todavía no está vinculado al sistema de acceso");
        }
        if (!keycloak.passwordEsValida(yo.getEmail(), req.passwordActual())) {
            throw new ConflictException("La contraseña actual no es correcta");
        }
        keycloak.resetPassword(yo.getKeycloakUserId(), req.passwordNueva());
        log.info("Contraseña de {} cambiada por ella misma", yo.getEmail());
        return ResponseEntity.noContent().build();
    }

    /**
     * C-17 — sube o reemplaza la foto. Se guarda redimensionada; devuelve el data URI ya listo.
     * Va embebida en {@code GET /me} (es un thumbnail de pocos KB), así el front pinta el avatar
     * sin un segundo request autenticado.
     */
    @PostMapping("/foto")
    public ResponseEntity<Map<String, String>> subir(@RequestPart("foto") MultipartFile foto) {
        Nutricionista yo = nutricionistaService.getCurrent();
        archivoService.guardar(yo.getId(), TipoArchivo.FOTO_PERFIL, foto);
        return ResponseEntity.ok(Map.of("foto", archivoService.fotoDataUri(yo.getId())));
    }

    @DeleteMapping("/foto")
    public ResponseEntity<Void> borrar() {
        archivoService.borrar(nutricionistaService.getCurrent().getId(), TipoArchivo.FOTO_PERFIL);
        return ResponseEntity.noContent().build();
    }
}
