package com.nutriapp.modules.receta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecetaVencimientoJobTest {

    @Mock RecetaRepository repo;
    @InjectMocks RecetaVencimientoJob job;

    @Test
    void vencePendientesExpiradas_lasMarcaVencidas() {
        Receta r1 = pendiente();
        Receta r2 = pendiente();
        when(repo.findByEstadoAndVenceAtBeforeAndDeletedAtIsNull(eq(EstadoReceta.PENDIENTE), any(LocalDate.class)))
                .thenReturn(List.of(r1, r2));

        int n = job.vencerPendientesExpiradas();

        assertThat(n).isEqualTo(2);
        assertThat(r1.getEstado()).isEqualTo(EstadoReceta.VENCIDA);
        assertThat(r2.getEstado()).isEqualTo(EstadoReceta.VENCIDA);
        verify(repo).saveAll(List.of(r1, r2));
    }

    @Test
    void sinExpiradas_noGuardaNada() {
        when(repo.findByEstadoAndVenceAtBeforeAndDeletedAtIsNull(eq(EstadoReceta.PENDIENTE), any(LocalDate.class)))
                .thenReturn(List.of());

        int n = job.vencerPendientesExpiradas();

        assertThat(n).isZero();
        verify(repo, never()).saveAll(any());
    }

    private Receta pendiente() {
        Receta r = new Receta();
        r.setEstado(EstadoReceta.PENDIENTE);
        return r;
    }
}
