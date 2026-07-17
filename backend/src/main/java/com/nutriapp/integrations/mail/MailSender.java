package com.nutriapp.integrations.mail;

/**
 * Port de envío de mail. Impl real (Fase 2): SmtpMailSender (proveedor TBD con el cliente).
 * El dispatcher de notificaciones (Fase 1) reintenta las QUEUED contra este port.
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
