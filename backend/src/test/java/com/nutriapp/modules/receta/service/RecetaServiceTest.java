package com.nutriapp.modules.receta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.modules.configuracion.service.ConfiguracionService;
import com.nutriapp.modules.notificacion.service.NotificacionService;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.paciente.mapper.PacienteMapper;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.producto.mapper.ProductoMapper;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import com.nutriapp.modules.receta.RecetaProperties;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.util.List;
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
class RecetaServiceTest {

    @Mock RecetaRepository repo;
    @Mock PacienteRepository pacienteRepository;
    @Mock ProductoRepository productoRepository;
    @Mock PacienteMapper pacienteMapper;
    @Mock ProductoMapper productoMapper;
    @Mock NutricionistaService nutricionistaService;
    @Mock NotificacionService notificacionService;
    @Mock CodigoGenerator codigoGenerator;
    @Mock TiendaNubeClient tiendaNubeClient;
    @Mock RecetaProperties props;
    @Mock ConfiguracionService configuracionService;

    @InjectMocks RecetaService service;

    private Nutricionista nutri;
    private final UUID recetaId = UUID.randomUUID();
    private final UUID pacienteId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        nutri = new Nutricionista();
        nutri.setId(UUID.randomUUID());
        when(nutricionistaService.getCurrent()).thenReturn(nutri);
        when(repo.save(any(Receta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(new Paciente()));
        when(notificacionService.forReceta(recetaId)).thenReturn(List.of());
    }

    private Receta receta(EstadoReceta estado) {
        Receta r = new Receta();
        r.setId(recetaId);
        r.setCodigo("RX-TEST01");
        r.setEstado(estado);
        r.setPacienteId(pacienteId);
        when(repo.findByIdAndNutricionistaIdAndDeletedAtIsNull(recetaId, nutri.getId()))
                .thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void anular_pendiente_borraCuponYCancelaNotificaciones() {
        Receta r = receta(EstadoReceta.PENDIENTE);
        r.setCuponTiendanubeId(555L);

        var resp = service.anular(recetaId);

        assertThat(r.getEstado()).isEqualTo(EstadoReceta.ANULADA);
        assertThat(r.getAnuladaAt()).isNotNull();
        assertThat(resp.estado()).isEqualTo("ANULADA");
        verify(tiendaNubeClient).deleteCoupon(555L);
        verify(notificacionService).cancelarPendientes(recetaId);
    }

    @Test
    void anular_noPendiente_lanza409YNoTocaCuponNiNotificaciones() {
        receta(EstadoReceta.APLICADA);

        assertThatThrownBy(() -> service.anular(recetaId)).isInstanceOf(ConflictException.class);

        verify(tiendaNubeClient, never()).deleteCoupon(anyLong());
        verify(notificacionService, never()).cancelarPendientes(any());
    }

    @Test
    void anular_cuponEnStub_degradaSinRomper() {
        Receta r = receta(EstadoReceta.PENDIENTE);
        r.setCuponTiendanubeId(777L);
        doThrow(new IntegrationUnavailableException("tiendanube")).when(tiendaNubeClient).deleteCoupon(777L);

        var resp = service.anular(recetaId); // no debe propagar la excepción

        assertThat(r.getEstado()).isEqualTo(EstadoReceta.ANULADA);
        assertThat(resp.estado()).isEqualTo("ANULADA");
        verify(notificacionService).cancelarPendientes(recetaId);
    }

    @Test
    void anular_sinCupon_noLlamaTiendaNube() {
        receta(EstadoReceta.PENDIENTE); // cuponTiendanubeId null

        service.anular(recetaId);

        verify(tiendaNubeClient, never()).deleteCoupon(anyLong());
    }

    @Test
    void reenviar_pendiente_reencola() {
        Paciente paciente = new Paciente();
        Receta r = receta(EstadoReceta.PENDIENTE);
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));

        service.reenviar(recetaId);

        verify(notificacionService).reencolar(r, paciente);
    }

    @Test
    void reenviar_noPendiente_lanza409() {
        receta(EstadoReceta.VENCIDA);

        assertThatThrownBy(() -> service.reenviar(recetaId)).isInstanceOf(ConflictException.class);

        verify(notificacionService, never()).reencolar(any(), any());
    }
}
