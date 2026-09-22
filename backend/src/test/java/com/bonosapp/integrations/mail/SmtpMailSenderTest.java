package com.bonosapp.integrations.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.IntegrationsProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpMailSenderTest {

    private final IntegrationsProperties.Mail props =
            new IntegrationsProperties.Mail("live", "no-reply@bonosapp.local", "BonosApp");

    @Test
    void arma_from_to_subject_yEnvia() throws Exception {
        JavaMailSender java = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage((Session) null);
        when(java.createMimeMessage()).thenReturn(mime);

        new SmtpMailSender(java, props).send("paciente@x.com", "Tu receta RX-1", "Cuerpo de la receta");

        verify(java).send(mime);
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("paciente@x.com");
        assertThat(mime.getSubject()).isEqualTo("Tu receta RX-1");
        assertThat(mime.getFrom()[0].toString()).contains("no-reply@bonosapp.local");
        assertThat(mime.getFrom()[0].toString()).contains("BonosApp");
    }

    /** F-20: el PDF del bono viaja pegado al mail, con su nombre y su content-type. */
    @Test
    void conAdjunto_mandaMultipartConElArchivo() throws Exception {
        JavaMailSender java = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage((Session) null);
        when(java.createMimeMessage()).thenReturn(mime);

        new SmtpMailSender(java, props).send("paciente@x.com", "Tu bono RX-1", "Cuerpo",
                List.of(MailSender.Adjunto.pdf("bono-RX-1.pdf", "%PDF-1.4 falso".getBytes())));

        verify(java).send(mime);
        assertThat(mime.getContent()).isInstanceOf(jakarta.mail.Multipart.class);
        jakarta.mail.Multipart partes = (jakarta.mail.Multipart) mime.getContent();
        boolean tieneElPdf = false;
        for (int i = 0; i < partes.getCount(); i++) {
            if ("bono-RX-1.pdf".equals(partes.getBodyPart(i).getFileName())) {
                tieneElPdf = true;
            }
        }
        assertThat(tieneElPdf).isTrue();
    }

    /** Sin adjuntos el mail sigue siendo simple: no se vuelve multipart porque sí. */
    @Test
    void sinAdjuntos_noSeVuelveMultipart() throws Exception {
        JavaMailSender java = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage((Session) null);
        when(java.createMimeMessage()).thenReturn(mime);

        new SmtpMailSender(java, props).send("a@b.com", "s", "cuerpo");

        assertThat(mime.getContent()).isInstanceOf(String.class);
    }

    @Test
    void adjuntoVacio_seRechazaAlConstruirlo() {
        assertThatThrownBy(() -> MailSender.Adjunto.pdf("vacio.pdf", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacio.pdf");
    }

    @Test
    void fallaDeEnvio_seTraduceAExcepcion() {
        JavaMailSender java = mock(JavaMailSender.class);
        when(java.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP caído")).when(java).send(any(MimeMessage.class));

        assertThatThrownBy(() -> new SmtpMailSender(java, props).send("a@b.com", "s", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a@b.com");
    }
}
