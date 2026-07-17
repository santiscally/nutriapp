package com.nutriapp.integrations.whatsapp;

import com.nutriapp.integrations.IntegrationUnavailableException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StubWhatsAppSender implements WhatsAppSender {

    @Override
    public void send(String toE164, String body) {
        log.info("[stub-whatsapp] a={} — sin conexión, la notificación sigue QUEUED", toE164);
        throw new IntegrationUnavailableException("whatsapp");
    }
}
