package com.nutriapp.common.web;

import com.nutriapp.common.auth.AuthUtils;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.service.ArchivoService;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Identidad + estado del usuario logueado. Lo consume el front al iniciar sesión. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final NutricionistaService nutricionistaService;
    private final ArchivoService archivoService;

    @GetMapping
    public MeResponse me() {
        List<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .toList();

        String estadoValidacion = nutricionistaService.findCurrent()
                .map(Nutricionista::getEstadoValidacion)
                .map(Enum::name)
                .orElse(null);

        String nombre = nutricionistaService.findCurrent().map(Nutricionista::getNombre)
                .orElse(AuthUtils.currentJwt().map(j -> j.getClaimAsString("given_name")).orElse(null));
        String apellido = nutricionistaService.findCurrent().map(Nutricionista::getApellido)
                .orElse(AuthUtils.currentJwt().map(j -> j.getClaimAsString("family_name")).orElse(null));

        // C-17: la foto va embebida como data URI. Es un thumbnail de pocos KB y evita que el
        // front tenga que hacer un segundo request con Bearer sólo para pintar el avatar.
        String foto = nutricionistaService.findCurrent()
                .map(n -> archivoService.fotoDataUri(n.getId()))
                .orElse(null);

        // V011: el descuento es propio de cada nutricionista. Viaja acá porque la pantalla de
        // emisión lo muestra como dato de sólo lectura y antes lo sacaba de GET /configuracion,
        // que dejó de existir junto con el valor global.
        BigDecimal descuentoPct = nutricionistaService.findCurrent()
                .map(Nutricionista::getDescuentoPct)
                .orElse(null);

        return new MeResponse(
                AuthUtils.currentUserId().map(Object::toString).orElse(null),
                nombre,
                apellido,
                AuthUtils.currentEmail().orElse(null),
                AuthUtils.currentRoles(),
                authorities,
                estadoValidacion,
                foto,
                descuentoPct);
    }

    public record MeResponse(
            String id,
            String nombre,
            String apellido,
            String email,
            List<String> roles,
            List<String> authorities,
            String estadoValidacion,
            /** C-17: data URI del avatar, o null si no cargó foto. */
            String foto,
            /** % de descuento propio. null para el admin, que no emite recetas (C-07). */
            BigDecimal descuentoPct
    ) {}
}
