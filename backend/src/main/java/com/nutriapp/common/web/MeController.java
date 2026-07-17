package com.nutriapp.common.web;

import com.nutriapp.common.auth.AuthUtils;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
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

        return new MeResponse(
                AuthUtils.currentUserId().map(Object::toString).orElse(null),
                nombre,
                apellido,
                AuthUtils.currentEmail().orElse(null),
                AuthUtils.currentRoles(),
                authorities,
                estadoValidacion);
    }

    public record MeResponse(
            String id,
            String nombre,
            String apellido,
            String email,
            List<String> roles,
            List<String> authorities,
            String estadoValidacion
    ) {}
}
