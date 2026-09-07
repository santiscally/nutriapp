package com.bonosapp.modules.paciente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.common.error.ConflictException;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.service.NutricionistaService;
import com.bonosapp.modules.paciente.entity.Paciente;
import com.bonosapp.modules.paciente.mapper.PacienteMapper;
import com.bonosapp.modules.paciente.repository.PacienteRepository;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PacienteServiceTest {

    @Mock PacienteRepository repository;
    @Mock PacienteMapper mapper;
    @Mock NutricionistaService nutricionistaService;
    @Mock RecetaRepository recetaRepository;

    @InjectMocks PacienteService service;

    private Nutricionista nutri;
    private Paciente paciente;
    private final UUID pacienteId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        nutri = new Nutricionista();
        nutri.setId(UUID.randomUUID());
        paciente = new Paciente();
        paciente.setId(pacienteId);
        when(nutricionistaService.getCurrent()).thenReturn(nutri);
        when(repository.findByIdAndNutricionistaIdAndDeletedAtIsNull(pacienteId, nutri.getId()))
                .thenReturn(Optional.of(paciente));
    }

    @Test
    void delete_conRecetasPendientes_lanza409YNoBorra() {
        when(recetaRepository.existsByPacienteIdAndEstadoAndDeletedAtIsNull(pacienteId, EstadoReceta.PENDIENTE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.delete(pacienteId)).isInstanceOf(ConflictException.class);

        assertThat(paciente.isDeleted()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void delete_sinPendientes_softDelete() {
        when(recetaRepository.existsByPacienteIdAndEstadoAndDeletedAtIsNull(pacienteId, EstadoReceta.PENDIENTE))
                .thenReturn(false);

        service.delete(pacienteId);

        assertThat(paciente.isDeleted()).isTrue();
        verify(repository).save(paciente);
    }
}
