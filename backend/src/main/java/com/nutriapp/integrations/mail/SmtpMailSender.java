package com.nutriapp.integrations.mail;

import com.nutriapp.integrations.IntegrationsProperties;
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
 * <p>Hoy manda texto plano (los templates {@code NotificacionTemplates} son texto). El HTML con logo
 * inline por CID se arma en Fase 2 (tarea 2.3). Un fallo de envío se propaga como excepción normal:
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
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromAddress, fromName);
            } else {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.debug("[smtp-mail] enviado a={} asunto={}", to, subject);
        } catch (jakarta.mail.MessagingException | UnsupportedEncodingException | MailException ex) {
            throw new IllegalStateException("No se pudo enviar el email a " + to + ": " + ex.getMessage(), ex);
        }
    }
}
