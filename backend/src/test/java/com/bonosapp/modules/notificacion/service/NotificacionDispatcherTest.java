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
import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.notificacion.entity.CanalNotificacion;
import com.bonosapp.modules.notificacion.entity.TipoNotificacion;
import com.bonosapp.modules.notificacion.service.NotificacionService.NotificacionPendiente;
import com.bonosapp.modules.receta.dto.RecetaResponse;
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

/**
 * F-20 — el mail del bono <b>es</b> el bono: va en HTML con la plantilla del cliente y sin archivos
 * adjuntos. Lo que se verifica acá es la regla de negocio, no el HTML en sí: a qué mails se les
 * arma, a cuáles no, y qué pasa cuando no se puede armar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionDispatcherTest {

    @Mock NotificacionService service;
    @Mock MailSender mailSender;
    @Mock IntegrationHealthRegistry health;
    @Mock BonoDeSistema bonoDeSistema;
    @Mock BonoMailHtml bonoHtml;
    @Mock RecetaResponse receta;

    private NotificacionDispatcher dispatcher;

    private final UUID recetaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        dispatcher = new NotificacionDispatcher(
                service, mailSender,
                new NotificacionProperties(30000L, 5, 25, "admin@bonosapp.dev", "https://bonosapp.com.ar"),
                health, bonoDeSistema, bonoHtml);
    }

    private void enCola(TipoNotificacion tipo, UUID receta) {
        when(service.tomarLote(anyInt(), anyInt())).thenReturn(List.of(new NotificacionPendiente(
                UUID.randomUUID(), tipo, receta, CanalNotificacion.EMAIL,
                "paciente@example.com", "Tu bono profesional RX-1", "cuerpo")));
    }

    private void bonoResuelto() {
        when(bonoDeSistema.buscar(recetaId))
                .thenReturn(Optional.of(new BonoDeSistema.Datos(receta, "Ana Gómez")));
        when(bonoHtml.armar(receta, "Ana Gómez")).thenReturn("<html>el bono</html>");
    }

    private String htmlEnviado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(any(), any(), any(), captor.capture(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<MailSender.Adjunto> adjuntosEnviados() {
        ArgumentCaptor<List<MailSender.Adjunto>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailSender).send(any(), any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    /** El bono es el cuerpo del mail, no un archivo: va en HTML y sin adjuntos. */
    @Test
    void elMailDelBonoVaEnHtmlYSinAdjuntos() {
        enCola(TipoNotificacion.EMISION_RECETA, recetaId);
        bonoResuelto();

        dispatcher.dispatch();

        assertThat(htmlEnviado()).isEqualTo("<html>el bono</html>");
        assertThat(adjuntosEnviados()).isEmpty();
    }

    /** Un aviso del alta no es un bono: sigue siendo texto plano y ni siquiera se busca la receta. */
    @Test
    void losAvisosDeRegistroVanEnTextoPlano() {
        enCola(TipoNotificacion.REGISTRO_APROBADO, null);

        dispatcher.dispatch();

        assertThat(htmlEnviado()).isNull();
        verify(bonoDeSistema, never()).buscar(any());
    }

    /**
     * Si el HTML no se puede armar, el mail <b>igual sale</b> en texto plano: el código del cupón y
     * el link van en el cuerpo de texto, que es lo que la paciente necesita. Quedarse sin mandarlo
     * cambiaría un problema cosmético por uno real.
     */
    @Test
    void siElHtmlFallaElMailSaleIgualEnTextoPlano() {
        enCola(TipoNotificacion.EMISION_RECETA, recetaId);
        when(bonoDeSistema.buscar(recetaId)).thenThrow(new IllegalStateException("boom"));

        dispatcher.dispatch();

        assertThat(htmlEnviado()).isNull();
        verify(service).marcarEnviada(any());
    }

    /** Un fallo del proveedor de mail sigue siendo un fallo, con HTML o sin él. */
    @Test
    void siElMailFallaLaNotificacionNoQuedaComoEnviada() {
        enCola(TipoNotificacion.EMISION_RECETA, recetaId);
        bonoResuelto();
        doThrow(new RuntimeException("smtp caído"))
                .when(mailSender).send(any(), any(), any(), any(), any());

        dispatcher.dispatch();

        verify(service, never()).marcarEnviada(any());
        verify(service).marcarFallo(any(), any(), eq(5));
    }
}
