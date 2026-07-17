package com.nutriapp.integrations.mail;

import com.nutriapp.integrations.IntegrationUnavailableException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StubMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[stub-mail] a={} asunto={} — sin conexión, la notificación sigue QUEUED", to, subject);
        throw new IntegrationUnavailableException("mail");
    }
}
