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
