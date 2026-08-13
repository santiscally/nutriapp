package com.nutriapp.modules.paciente.service;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import com.nutriapp.modules.paciente.dto.PacienteCreateRequest;
import com.nutriapp.modules.paciente.dto.PacienteResponse;
import com.nutriapp.modules.paciente.dto.PacienteUpdateRequest;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.paciente.mapper.PacienteMapper;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository repository;
    private final PacienteMapper mapper;
    private final NutricionistaService nutricionistaService;
    private final RecetaRepository recetaRepository;

    @Transactional(readOnly = true)
    public Page<PacienteResponse> search(String q, Pageable pageable) {
        UUID nutriId = nutricionistaService.getCurrent().getId();
        return repository.search(nutriId, q, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PacienteResponse get(UUID id) {
        return mapper.toResponse(getOwned(id));
    }

    @Transactional
    public PacienteResponse create(PacienteCreateRequest req) {
        Nutricionista nutri = nutricionistaService.getCurrentAprobado();
        if (repository.existsByNutricionistaIdAndEmailIgnoreCaseAndDeletedAtIsNull(nutri.getId(), req.email())) {
            throw new ConflictException("Ya tenés un paciente con ese email");
        }
        Paciente p = new Paciente();
        p.setNutricionistaId(nutri.getId());
        p.setNombre(req.nombre());
        p.setApellido(req.apellido());
        p.setEmail(req.email());
        p.setWhatsapp(req.whatsapp());
        p.setFechaNacimiento(req.fechaNacimiento());
        p.setNotas(req.notas());
        return mapper.toResponse(repository.save(p));
    }

    @Transactional
    public PacienteResponse update(UUID id, PacienteUpdateRequest req) {
        Paciente p = getOwned(id);
        if (!p.getEmail().equalsIgnoreCase(req.email())
                && repository.existsByNutricionistaIdAndEmailIgnoreCaseAndDeletedAtIsNull(p.getNutricionistaId(), req.email())) {
            throw new ConflictException("Ya tenés otro paciente con ese email");
        }
        p.setNombre(req.nombre());
        p.setApellido(req.apellido());
        p.setEmail(req.email());
        p.setWhatsapp(req.whatsapp());
        p.setFechaNacimiento(req.fechaNacimiento());
        p.setNotas(req.notas());
        return mapper.toResponse(repository.save(p));
    }

    @Transactional
    public void delete(UUID id) {
        Paciente p = getOwned(id);
        if (recetaRepository.existsByPacienteIdAndEstadoAndDeletedAtIsNull(p.getId(), EstadoReceta.PENDIENTE)) {
            throw new ConflictException("No se puede eliminar: el paciente tiene bonos profesionales pendientes");
        }
        p.softDelete();
        repository.save(p);
    }

    private Paciente getOwned(UUID id) {
        UUID nutriId = nutricionistaService.getCurrent().getId();
        return repository.findByIdAndNutricionistaIdAndDeletedAtIsNull(id, nutriId)
                .orElseThrow(() -> new NotFoundException("Paciente no encontrado"));
    }
}
