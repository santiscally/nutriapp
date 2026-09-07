package com.bonosapp.modules.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.notificacion.entity.CanalNotificacion;
import com.bonosapp.modules.notificacion.entity.EstadoNotificacion;
import com.bonosapp.modules.notificacion.entity.Notificacion;
import com.bonosapp.modules.notificacion.entity.TipoNotificacion;
import com.bonosapp.modules.notificacion.repository.NotificacionRepository;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.paciente.entity.Paciente;
import com.bonosapp.modules.receta.entity.Receta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionServiceTest {

    @Mock NotificacionRepository repo;
    @Mock NotificacionTemplates templates;

    private NotificacionService service;

    private Receta receta;
    private Paciente paciente;
    private Nutricionista nutricionista;
    private final UUID recetaId = UUID.randomUUID();
    private final UUID nutricionistaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new NotificacionService(repo, templates, props("admin@bonosapp.dev"));

        receta = new Receta();
        receta.setId(recetaId);
        paciente = new Paciente();
        paciente.setEmail("juan@example.com");
        paciente.setWhatsapp("+5491144443333");
        nutricionista = new Nutricionista();
        nutricionista.setId(nutricionistaId);
        nutricionista.setNombre("Ana");
        nutricionista.setApellido("Gómez");
        nutricionista.setEmail("ana@example.com");
        when(templates.asuntoEmail(any())).thenReturn("asunto");
        when(templates.cuerpoEmail(any(), any())).thenReturn("cuerpo mail");
    }

    private NotificacionProperties props(String adminEmail) {
        return new NotificacionProperties(30000L, 5, 25, adminEmail, "https://bonosapp.com.ar");
    }

    /** 2.4: el único canal automático es el email — WhatsApp lo manda la nutricionista por wa.me. */
    @Test
    void encolar_creaSoloElEmailEnQueued() {
        when(repo.findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(recetaId)).thenReturn(List.of());

        service.encolarEmisionReceta(receta, paciente);

        ArgumentCaptor<Notificacion> cap = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo, times(1)).save(cap.capture());
        Notificacion email = cap.getValue();
        assertThat(email.getCanal()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(email.getEstado()).isEqualTo(EstadoNotificacion.QUEUED);
        assertThat(email.getRecetaId()).isEqualTo(recetaId);
        assertThat(email.getTipo()).isEqualTo(TipoNotificacion.EMISION_RECETA);
        assertThat(email.getDestinatario()).isEqualTo("juan@example.com");
    }

    @Test
    void encolarRegistro_avisaALaNutricionistaYAlAdmin() {
        service.encolarRegistro(nutricionista);

        ArgumentCaptor<Notificacion> cap = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo, times(2)).save(cap.capture());
        List<Notificacion> encoladas = cap.getAllValues();
        assertThat(encoladas).allSatisfy(n -> {
            assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.QUEUED);
            assertThat(n.getCanal()).isEqualTo(CanalNotificacion.EMAIL);
            assertThat(n.getNutricionistaId()).isEqualTo(nutricionistaId);
            assertThat(n.getRecetaId()).isNull();
        });
        assertThat(encoladas).extracting(Notificacion::getTipo)
                .containsExactly(TipoNotificacion.REGISTRO_RECIBIDO, TipoNotificacion.ADMIN_NUEVA_SOLICITUD);
        assertThat(encoladas).extracting(Notificacion::getDestinatario)
                .containsExactly("ana@example.com", "admin@bonosapp.dev");
    }

    /** Sin casilla de admin configurada, el acuse a la nutricionista igual sale. */
    @Test
    void encolarRegistro_sinAdminEmail_soloEncolaElAcuse() {
        service = new NotificacionService(repo, templates, props(" "));

        service.encolarRegistro(nutricionista);

        ArgumentCaptor<Notificacion> cap = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo, times(1)).save(cap.capture());
        assertThat(cap.getValue().getTipo()).isEqualTo(TipoNotificacion.REGISTRO_RECIBIDO);
    }

    @Test
    void encolarAprobacion_yRechazo_vanALaNutricionista() {
        service.encolarAprobacion(nutricionista);
        service.encolarRechazo(nutricionista, "La matrícula no es legible");

        ArgumentCaptor<Notificacion> cap = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo, times(2)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(Notificacion::getTipo)
                .containsExactly(TipoNotificacion.REGISTRO_APROBADO, TipoNotificacion.REGISTRO_RECHAZADO);
        assertThat(cap.getAllValues()).allMatch(n -> n.getDestinatario().equals("ana@example.com"));
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
        // Se reusa, no se crea otra fila → un solo save.
        verify(repo, times(1)).save(any());
    }

    @Test
    void cancelarPendientes_softDeleteSoloLasQueued() {
        Notificacion queued = new Notificacion();
        queued.setCanal(CanalNotificacion.EMAIL);
        queued.setEstado(EstadoNotificacion.QUEUED);
        Notificacion sent = new Notificacion();
        sent.setCanal(CanalNotificacion.EMAIL);
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
