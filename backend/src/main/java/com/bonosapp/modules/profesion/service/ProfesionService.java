package com.bonosapp.modules.profesion.service;

import com.bonosapp.common.error.UnprocessableException;
import com.bonosapp.modules.profesion.dto.ProfesionResponse;
import com.bonosapp.modules.profesion.entity.Profesion;
import com.bonosapp.modules.profesion.repository.ProfesionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfesionService {

    private final ProfesionRepository repository;

    @Transactional(readOnly = true)
    public List<ProfesionResponse> listar() {
        return repository.findByActivoTrueAndDeletedAtIsNullOrderByNombreAsc().stream()
                .map(p -> new ProfesionResponse(p.getId(), p.getNombre()))
                .toList();
    }

    /**
     * Normaliza el nombre recibido al de la lista, o falla. Null pasa: el campo todavía es opcional
     * mientras el front viejo siga registrando sin él.
     */
    @Transactional(readOnly = true)
    public String validar(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        return repository.findActivaPorNombre(nombre.trim())
                .map(Profesion::getNombre)
                .orElseThrow(() -> new UnprocessableException(
                        "La profesión \"" + nombre.trim() + "\" no está en la lista."));
    }
}
