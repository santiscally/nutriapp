package com.bonosapp.integrations.mail;

import com.bonosapp.integrations.IntegrationUnavailableException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StubMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body, String html, java.util.List<Adjunto> adjuntos) {
        log.info("[stub-mail] a={} asunto={} html={} adjuntos={} — sin conexión, la notificación sigue QUEUED",
                to, subject, html != null && !html.isBlank(), adjuntos == null ? 0 : adjuntos.size());
        throw new IntegrationUnavailableException("mail");
    }
}
