package com.bonosapp.integrations.mail;

import java.util.List;

/**
 * Port de envío de mail. Impl real: {@link SmtpMailSender}; en {@code stub} degrada y la
 * notificación queda QUEUED. El dispatcher de notificaciones reintenta contra este port.
 */
public interface MailSender {

    /**
     * Envío con adjuntos. Es el método que implementan los senders — la sobrecarga sin adjuntos
     * delega acá. Hacerlo al revés (un default que ignore la lista) dejaría que un sender mande el
     * mail <b>sin</b> el PDF sin que nadie se entere.
     */
    void send(String to, String subject, String body, List<Adjunto> adjuntos);

    default void send(String to, String subject, String body) {
        send(to, subject, body, List.of());
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
