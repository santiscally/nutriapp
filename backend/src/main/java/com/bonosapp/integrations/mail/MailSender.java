package com.bonosapp.integrations.mail;

import java.util.List;

/**
 * Port de envío de mail. Impl real: {@link SmtpMailSender}; en {@code stub} degrada y la
 * notificación queda QUEUED. El dispatcher de notificaciones reintenta contra este port.
 */
public interface MailSender {

    /**
     * Envío completo. Es el método que implementan los senders — las sobrecargas delegan acá.
     * Hacerlo al revés (defaults que ignoren el HTML o los adjuntos) dejaría que un sender mande
     * un mail incompleto sin que nadie se entere.
     *
     * @param body texto plano; viaja SIEMPRE, también cuando hay HTML. Es lo que ve quien tiene el
     *             HTML desactivado y lo que leen los filtros de spam, así que nunca es opcional.
     * @param html cuerpo HTML, o null para mandar sólo texto.
     */
    void send(String to, String subject, String body, String html, List<Adjunto> adjuntos);

    default void send(String to, String subject, String body, List<Adjunto> adjuntos) {
        send(to, subject, body, null, adjuntos);
    }

    default void send(String to, String subject, String body) {
        send(to, subject, body, null, List.of());
    }

    /** Un archivo que viaja pegado al mail. {@code contenido} ya en memoria: son PDFs de pocos KB. */
    record Adjunto(String nombreArchivo, String contentType, byte[] contenido) {

        public Adjunto {
            if (nombreArchivo == null || nombreArchivo.isBlank()) {
                throw new IllegalArgumentException("El adjunto necesita un nombre de archivo");
            }
            if (contenido == null || contenido.length == 0) {
                throw new IllegalArgumentException("Adjunto vacío: " + nombreArchivo);
            }
            contentType = contentType == null || contentType.isBlank()
                    ? "application/octet-stream"
                    : contentType;
        }

        public static Adjunto pdf(String nombreArchivo, byte[] contenido) {
            return new Adjunto(nombreArchivo, "application/pdf", contenido);
        }
    }
}
