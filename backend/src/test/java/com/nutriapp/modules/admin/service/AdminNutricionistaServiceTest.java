package com.nutriapp.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.integrations.keycloak.KeycloakAdminClient;
import com.nutriapp.modules.nutricionista.entity.EstadoValidacion;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
import com.nutriapp.modules.nutricionista.service.ArchivoService;
import com.nutriapp.modules.nutricionista.service.ParametrosNegocioService;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Gestión de cuentas del admin: desactivar/reactivar, borrar y resetear contraseña. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminNutricionistaServiceTest {

    @Mock NutricionistaRepository repository;
    @Mock KeycloakAdminClient keycloak;
    @Mock ArchivoService archivoService;
    @Mock RecetaRepository recetaRepository;
    @Mock PacienteRepository pacienteRepository;

    private AdminNutricionistaService service;

    private final UUID id = UUID.randomUUID();
    private Nutricionista nutri;

    @BeforeEach
    void setup() {
        service = new AdminNutricionistaService(
                repository, keycloak, new ParametrosNegocioService(repository), archivoService,
                recetaRepository, pacienteRepository);

        nutri = new Nutricionista();
        nutri.setId(id);
        nutri.setEmail("ana@nutriapp.dev");
        nutri.setKeycloakUserId("kc-123");
        nutri.setEstadoValidacion(EstadoValidacion.APROBADA);
        nutri.setActivo(true);
        nutri.setDescuentoPct(new BigDecimal("15.00"));
        nutri.setComisionPct(new BigDecimal("10.00"));

        when(repository.findById(id)).thenReturn(Optional.of(nutri));
        when(repository.save(any(Nutricionista.class))).thenAnswer(inv -> inv.getArgument(0));
        when(archivoService.buscar(any(), any())).thenReturn(Optional.empty());
    }

    // --- desactivar / reactivar ---

    @Test
    void desactivar_cortaElAccesoEnKeycloakYConservaElEstadoDeValidacion() {
        var resp = service.desactivar(id);

        verify(keycloak).setEnabled("kc-123", false);
        assertThat(nutri.isActivo()).isFalse();
        // Sigue APROBADA: dar de baja no es rechazar la solicitud.
        assertThat(nutri.getEstadoValidacion()).isEqualTo(EstadoValidacion.APROBADA);
        assertThat(resp.activo()).isFalse();
    }

    @Test
    void reactivar_devuelveElAcceso() {
        nutri.setActivo(false);

        var resp = service.reactivar(id);

        verify(keycloak).setEnabled("kc-123", true);
        assertThat(resp.activo()).isTrue();
    }

    @Test
    void reactivar_soloAplicaSobreUnaSolicitudAprobada() {
        nutri.setEstadoValidacion(EstadoValidacion.RECHAZADA);

        assertThatThrownBy(() -> service.reactivar(id)).isInstanceOf(ConflictException.class);

        verify(keycloak, never()).setEnabled(anyString(), any(Boolean.class));
    }

    // --- eliminar ---

    @Test
    void eliminar_borraKeycloakPacientesArchivosYLaFila() {
        when(recetaRepository.countByNutricionistaId(id)).thenReturn(0L);

        service.eliminar(id);

        verify(keycloak).deleteUserOrFail("kc-123");
        verify(pacienteRepository).deleteByNutricionistaId(id);
        verify(archivoService).borrarTodos(id);
        verify(repository).delete(nutri);
    }

    /**
     * El guard que protege los cierres: una receta emitida es plata contabilizada y borrar a su
     * autora la dejaría sin dueño. Se responde 409 con la salida (desactivar) en el mensaje.
     */
    @Test
    void eliminar_conRecetasEmitidas_lanza409YNoTocaNada() {
        when(recetaRepository.countByNutricionistaId(id)).thenReturn(3L);

        assertThatThrownBy(() -> service.eliminar(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3 recetas emitidas")
                .hasMessageContaining("Desactivala");

        verify(keycloak, never()).deleteUserOrFail(anyString());
        verify(pacienteRepository, never()).deleteByNutricionistaId(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void eliminar_siKeycloakFalla_noBorraLaFilaLocal() {
        when(recetaRepository.countByNutricionistaId(id)).thenReturn(0L);
        org.mockito.Mockito.doThrow(new IllegalStateException("keycloak caído"))
                .when(keycloak).deleteUserOrFail("kc-123");

        assertThatThrownBy(() -> service.eliminar(id)).isInstanceOf(IllegalStateException.class);

        // Si se borrara igual, quedaría un usuario capaz de loguearse sin perfil.
        verify(repository, never()).delete(any());
    }

    @Test
    void eliminar_inexistente_lanza404() {
        UUID otro = UUID.randomUUID();
        when(repository.findById(otro)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(otro)).isInstanceOf(NotFoundException.class);
    }

    // --- reset de contraseña ---

    @Test
    void resetearPassword_cambiaLaClaveYLimpiaLosIntentosFallidos() {
        service.resetearPassword(id, "nueva-clave-123");

        verify(keycloak).resetPassword("kc-123", "nueva-clave-123");
        // Sin esto, alguien frenado por la protección de fuerza bruta seguiría sin poder entrar
        // aunque la contraseña nueva fuera correcta.
        verify(keycloak).limpiarIntentosFallidos("kc-123");
    }

    @Test
    void resetearPassword_sinUsuarioEnKeycloak_lanza409() {
        nutri.setKeycloakUserId(null);

        assertThatThrownBy(() -> service.resetearPassword(id, "nueva-clave-123"))
                .isInstanceOf(ConflictException.class);

        verify(keycloak, never()).resetPassword(anyString(), anyString());
    }

    // --- parámetros ---

    @Test
    void actualizarParametros_guardaLosDosPorcentajes() {
        var resp = service.actualizarParametros(id, new BigDecimal("30"), new BigDecimal("8"));

        assertThat(nutri.getDescuentoPct()).isEqualByComparingTo("30");
        assertThat(nutri.getComisionPct()).isEqualByComparingTo("8");
        assertThat(resp.descuentoPct()).isEqualByComparingTo("30");
        assertThat(resp.comisionPct()).isEqualByComparingTo("8");
    }

    @Test
    void aprobar_habilitaElAccesoAdemasDeValidarLaSolicitud() {
        nutri.setEstadoValidacion(EstadoValidacion.PENDIENTE);
        nutri.setActivo(false);

        var resp = service.aprobar(id);

        verify(keycloak).setEnabled("kc-123", true);
        assertThat(resp.estadoValidacion()).isEqualTo("APROBADA");
        assertThat(resp.activo()).isTrue();
    }
}
