package com.bonosapp.modules.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.health.IntegrationHealthRegistry;
import com.bonosapp.integrations.mail.MailSender;
import com.bonosapp.modules.bonopdf.service.BonoPdfService;
import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.notificacion.entity.CanalNotificacion;
import com.bonosapp.modules.notificacion.entity.TipoNotificacion;
import com.bonosapp.modules.notificacion.service.NotificacionService.NotificacionPendiente;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

/**
 * F-20 — el bono va adjunto al mail del paciente. Lo que se verifica acá es la regla de negocio del
 * adjunto, no el PDF en sí: a qué mails se pega, a cuáles no, y qué pasa cuando no se puede armar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionDispatcherTest {

    @Mock NotificacionService service;
    @Mock MailSender mailSender;
    @Mock IntegrationHealthRegistry health;
    @Mock BonoPdfService bonoPdf;

    private NotificacionDispatcher dispatcher;

    private final UUID recetaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        dispatcher = new NotificacionDispatcher(
                service, mailSender,
                new NotificacionProperties(30000L, 5, 25, "admin@bonosapp.dev", "https://bonosapp.com.ar"),
                health, bonoPdf);
    }

    private void enCola(TipoNotificacion tipo, UUID receta) {
        when(service.tomarLote(anyInt(), anyInt())).thenReturn(List.of(new NotificacionPendiente(
                UUID.randomUUID(), tipo, receta, CanalNotificacion.EMAIL,
                "paciente@example.com", "Tu bono profesional RX-1", "cuerpo")));
    }

    @SuppressWarnings("unchecked")
    private List<MailSender.Adjunto> adjuntosEnviados() {
        ArgumentCaptor<List<MailSender.Adjunto>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailSender).send(any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void elMailDelBonoLlevaElPdfAdjunto() {
        enCola(TipoNotificacion.EMISION_RECETA, recetaId);
        when(bonoPdf.generarDeSistema(recetaId))
                .thenReturn(new BonoPdfService.Bono("bono-RX-1.pdf", new byte[] {1, 2, 3}));

        dispatcher.dispatch();

        assertThat(adjuntosEnviados()).singleElement().satisfies(a -> {
            assertThat(a.nombreArchivo()).isEqualTo("bono-RX-1.pdf");
            assertThat(a.contentType()).isEqualTo("application/pdf");
        });
    }

    /** Los avisos del alta no tienen bono: no hay nada que adjuntar ni PDF que armar. */
    @Test
    void losAvisosDeRegistroNoLlevanAdjunto() {
        enCola(TipoNotificacion.REGISTRO_APROBADO, null);

        dispatcher.dispatch();

        assertThat(adjuntosEnviados()).isEmpty();
        verify(bonoPdf, never()).generarDeSistema(any());
    }

    /**
     * Si el PDF no se puede armar, el mail <b>igual sale</b>: el código del cupón va en el cuerpo y
     * es lo que la paciente necesita. Quedarse sin mandarlo cambiaría un problema cosmético por uno
     * real.
     */
    @Test
    void siElPdfFallaElMailSaleIgualSinAdjunto() {
        enCola(TipoNotificacion.EMISION_RECETA, recetaId);
        when(bonoPdf.generarDeSistema(recetaId)).thenThrow(new IllegalStateException("boom"));

        dispatcher.dispatch();

        assertThat(adjuntosEnviados()).isEmpty();
        verify(service).marcarEnviada(any());
    }

    /** Un fallo del proveedor de mail sigue siendo un fallo, adjunto o no. */
    @Test
    void siElMailFallaLaNotificacionNoQuedaComoEnviada() {
        enCola(TipoNotificacion.EMISION_RECETA, recetaId);
        when(bonoPdf.generarDeSistema(recetaId))
                .thenReturn(new BonoPdfService.Bono("bono-RX-1.pdf", new byte[] {1}));
        doThrow(new RuntimeException("smtp caído"))
                .when(mailSender).send(any(), any(), any(), any());

        dispatcher.dispatch();

        verify(service, never()).marcarEnviada(any());
        verify(service).marcarFallo(any(), any(), eq(5));
    }
}
