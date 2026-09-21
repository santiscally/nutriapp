package com.bonosapp.modules.admin.service;

import com.bonosapp.modules.admin.dto.AdminRecetaResponse;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.receta.service.RecetaService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-14 — todos los bonos de todas las profesionales, con los mismos filtros que ve cada una en su
 * listado más el filtro por profesional.
 */
@Service
@RequiredArgsConstructor
public class AdminRecetaService {

    private final RecetaRepository repo;
    private final RecetaService recetaService;
    private final ProfesionalesLookup profesionales;

    @Transactional(readOnly = true)
    public Page<AdminRecetaResponse> search(EstadoReceta estado, UUID nutricionistaId, String q,
                                            LocalDate desde, LocalDate hasta, Pageable pageable) {
        Page<Receta> page = repo.search(nutricionistaId, estado, null, q,
                RecetaService.desdeInclusive(desde), RecetaService.hastaInclusive(hasta), pageable);
        Map<UUID, AdminRecetaResponse.Profesional> porId = profesionales.de(page.getContent());
        // Null cuando la cuenta se borró: el bono sigue existiendo y tiene que poder listarse.
        return page.map(r -> AdminRecetaResponse.de(
                recetaService.toResponse(r), porId.get(r.getNutricionistaId()), r.getOrdenTotal()));
    }
}
