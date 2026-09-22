package com.bonosapp.integrations.mail;

import com.bonosapp.integrations.IntegrationsProperties;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Envío de mail por SMTP (JavaMailSender autoconfigurado desde {@code spring.mail.*}). Se registra
 * sólo si {@code MAIL_MODE=live} (ver {@code IntegrationsConfig}); la conexión real es Fase 2, con
 * el proveedor que elija el cliente (recomendado: AWS SES por SMTP, STARTTLS 587).
 *
 * <p>Hoy manda texto plano (los templates {@code NotificacionTemplates} son texto) y adjunta archivos
 * cuando se los pasan (F-20: el PDF del bono). El HTML con logo inline por CID se arma en Fase 2. Un fallo de envío se propaga como excepción normal:
 * el {@code NotificacionDispatcher} lo cuenta como intento y reintenta hasta {@code maxIntentos}.
 */
@Slf4j
public class SmtpMailSender implements MailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpMailSender(JavaMailSender mailSender, IntegrationsProperties.Mail props) {
        this.mailSender = mailSender;
        this.fromAddress = props.fromAddress();
        this.fromName = props.fromName();
    }

    @Override
    public void send(String to, String subject, String body, java.util.List<Adjunto> adjuntos) {
        boolean conAdjuntos = adjuntos != null && !adjuntos.isEmpty();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart sólo cuando hace falta: un mail sin adjuntos viaja más liviano y no cambia
            // de forma respecto de lo que se venía mandando.
            MimeMessageHelper helper = new MimeMessageHelper(message, conAdjuntos, "UTF-8");
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromAddress, fromName);
            } else {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (conAdjuntos) {
                for (Adjunto a : adjuntos) {
                    helper.addAttachment(a.nombreArchivo(),
                            new org.springframework.core.io.ByteArrayResource(a.contenido()),
                            a.contentType());
                }
            }
            mailSender.send(message);
            log.debug("[smtp-mail] enviado a={} asunto={} adjuntos={}", to, subject,
                    conAdjuntos ? adjuntos.size() : 0);
        } catch (jakarta.mail.MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new IllegalStateException("No se pudo enviar el email a " + to + ": " + ex.getMessage(), ex);
        }
    }
}
