package com.nutriapp.modules.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.modules.notificacion.entity.CanalNotificacion;
import com.nutriapp.modules.notificacion.entity.EstadoNotificacion;
import com.nutriapp.modules.notificacion.entity.Notificacion;
import com.nutriapp.modules.notificacion.repository.NotificacionRepository;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.receta.entity.Receta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionServiceTest {

    @Mock NotificacionRepository repo;
    @Mock NotificacionTemplates templates;

    @InjectMocks NotificacionService service;

    private Receta receta;
    private Paciente paciente;
    private final UUID recetaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        receta = new Receta();
        receta.setId(recetaId);
        paciente = new Paciente();
        paciente.setEmail("juan@example.com");
        paciente.setWhatsapp("+5491144443333");
        when(templates.asuntoEmail(any())).thenReturn("asunto");
        when(templates.cuerpoEmail(any(), any())).thenReturn("cuerpo mail");
        when(templates.cuerpoWhatsApp(any(), any())).thenReturn("cuerpo wa");
    }

    @Test
    void encolar_creaEmailYWhatsappEnQueued() {
        when(repo.findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(recetaId)).thenReturn(List.of());

        service.encolarEmisionReceta(receta, paciente);

        ArgumentCaptor<Notificacion> cap = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo, times(2)).save(cap.capture());
        List<Notificacion> saved = cap.getAllValues();
        assertThat(saved).extracting(Notificacion::getCanal)
                .containsExactlyInAnyOrder(CanalNotificacion.EMAIL, CanalNotificacion.WHATSAPP);
        assertThat(saved).allMatch(n -> n.getEstado() == EstadoNotificacion.QUEUED);
        assertThat(saved).allMatch(n -> n.getRecetaId().equals(recetaId));
        Notificacion email = saved.stream().filter(n -> n.getCanal() == CanalNotificacion.EMAIL).findFirst().orElseThrow();
        assertThat(email.getDestinatario()).isEqualTo("juan@example.com");
        Notificacion wa = saved.stream().filter(n -> n.getCanal() == CanalNotificacion.WHATSAPP).findFirst().orElseThrow();
        assertThat(wa.getDestinatario()).isEqualTo("+5491144443333");
    }

    @Test
    void encolar_reusaFilaExistente_yResetaIntentos() {
        Notificacion existenteEmail = new Notificacion();
        existenteEmail.setCanal(CanalNotificacion.EMAIL);
        existenteEmail.setEstado(EstadoNotificacion.SENT);
        existenteEmail.setIntentos(3);
        when(repo.findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(recetaId))
                .thenReturn(List.of(existenteEmail));

        service.encolarEmisionReceta(receta, paciente);

        // La fila EMAIL existente se reusa (mismo objeto), vuelta a QUEUED con intentos 0.
        assertThat(existenteEmail.getEstado()).isEqualTo(EstadoNotificacion.QUEUED);
        assertThat(existenteEmail.getIntentos()).isZero();
        // Sólo se crea la de WhatsApp además de reusar la de email → 2 saves.
        verify(repo, times(2)).save(any());
    }

    @Test
    void cancelarPendientes_softDeleteSoloLasQueued() {
        Notificacion queued = new Notificacion();
        queued.setCanal(CanalNotificacion.EMAIL);
        queued.setEstado(EstadoNotificacion.QUEUED);
        Notificacion sent = new Notificacion();
        sent.setCanal(CanalNotificacion.WHATSAPP);
        sent.setEstado(EstadoNotificacion.SENT);
        when(repo.findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(recetaId))
                .thenReturn(List.of(queued, sent));

        service.cancelarPendientes(recetaId);

        assertThat(queued.isDeleted()).isTrue();
        assertThat(sent.isDeleted()).isFalse();
        ArgumentCaptor<List<Notificacion>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(cap.capture());
        assertThat(cap.getValue()).containsExactly(queued);
    }

    @Test
    void marcarFallo_pasaAFailedAlSuperarElTope() {
        UUID id = UUID.randomUUID();
        Notificacion n = new Notificacion();
        n.setEstado(EstadoNotificacion.QUEUED);
        n.setIntentos(4);
        when(repo.findById(id)).thenReturn(Optional.of(n));

        service.marcarFallo(id, "boom", 5);

        assertThat(n.getIntentos()).isEqualTo(5);
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FAILED);
    }

    @Test
    void marcarFallo_debajoDelTope_sigueQueued() {
        UUID id = UUID.randomUUID();
        Notificacion n = new Notificacion();
        n.setEstado(EstadoNotificacion.QUEUED);
        n.setIntentos(1);
        when(repo.findById(id)).thenReturn(Optional.of(n));

        service.marcarFallo(id, "boom", 5);

        assertThat(n.getIntentos()).isEqualTo(2);
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.QUEUED);
    }

    @Test
    void marcarSinConexion_noConsumeIntentos() {
        UUID id = UUID.randomUUID();
        Notificacion n = new Notificacion();
        n.setEstado(EstadoNotificacion.QUEUED);
        n.setIntentos(2);
        when(repo.findById(id)).thenReturn(Optional.of(n));

        service.marcarSinConexion(id, "sin conexión");

        assertThat(n.getIntentos()).isEqualTo(2);
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.QUEUED);
        assertThat(n.getLastError()).isEqualTo("sin conexión");
    }

    @Test
    void marcarEnviada_pasaASent() {
        UUID id = UUID.randomUUID();
        Notificacion n = new Notificacion();
        n.setEstado(EstadoNotificacion.QUEUED);
        when(repo.findById(id)).thenReturn(Optional.of(n));

        service.marcarEnviada(id);

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.SENT);
        assertThat(n.getSentAt()).isNotNull();
        verify(repo).save(n);
    }

    @Test
    void marcarEnviada_idInexistente_noRompe() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        service.marcarEnviada(id); // no debe lanzar

        verify(repo, never()).save(any());
    }
}
