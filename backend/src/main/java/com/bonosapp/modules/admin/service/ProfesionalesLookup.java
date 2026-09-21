package com.bonosapp.modules.admin.service;

import com.bonosapp.modules.admin.dto.AdminRecetaResponse;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.receta.entity.Receta;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resuelve de quién es cada bono en una sola query por página, no una por fila. */
@Component
@RequiredArgsConstructor
class ProfesionalesLookup {

    private final NutricionistaRepository repository;

    Map<UUID, AdminRecetaResponse.Profesional> de(List<Receta> recetas) {
        List<UUID> ids = recetas.stream()
                .map(Receta::getNutricionistaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AdminRecetaResponse.Profesional> porId = new HashMap<>();
        for (Nutricionista n : repository.findAllById(ids)) {
            porId.put(n.getId(), new AdminRecetaResponse.Profesional(
                    n.getId(), n.getNombre(), n.getApellido(), n.getEmail()));
        }
        return porId;
    }
}
