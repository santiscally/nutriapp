package com.bonosapp.integrations.mail;

import com.bonosapp.integrations.IntegrationUnavailableException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StubMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body, java.util.List<Adjunto> adjuntos) {
        log.info("[stub-mail] a={} asunto={} adjuntos={} — sin conexión, la notificación sigue QUEUED",
                to, subject, adjuntos == null ? 0 : adjuntos.size());
        throw new IntegrationUnavailableException("mail");
    }
}
