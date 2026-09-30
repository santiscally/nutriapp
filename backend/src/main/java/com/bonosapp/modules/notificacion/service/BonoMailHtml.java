package com.bonosapp.modules.notificacion.service;

import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * El cuerpo HTML del mail del bono, armado a imagen de la plantilla que mandó el cliente (F-20):
 * mismo logo, misma banda verde, mismo pie con la marca de TBC y el QR.
 *
 * <p><b>Por qué se rehace en HTML y no se manda la plantilla como imagen:</b> un mail que es una
 * sola imagen se ve en blanco en cuanto el cliente de correo bloquea imágenes —cosa que hacen por
 * defecto varios—, no se puede copiar el código de cupón, y los filtros de spam castigan el mail
 * sin texto. Acá el texto es texto: las imágenes son sólo el logo y el pie, y si no cargan el mail
 * se sigue entendiendo entero.
 *
 * <p><b>El texto no es exactamente el de la plantilla.</b> La plantilla decía "Adjuntamos el bono
 * profesional" porque nació pensada como PDF adjunto; ahora el bono <b>es</b> el mail, así que esa
 * frase mentiría. De paso, acá el texto lo escribimos nosotros: el typo del arte
 * ("imprimirel cupón") no se arrastra.
 *
 * <p><b>HTML de mail, no de web:</b> tablas y estilos en línea, ancho fijo de 600 px, nada de CSS
 * externo ni flex/grid — Gmail borra el {@code <style>} y Outlook no entiende la mitad de lo
 * moderno. Feo de escribir, pero es lo que se ve igual en todos lados.
 */
@Component
@RequiredArgsConstructor
public class BonoMailHtml {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    /** El verde de la banda de la plantilla, muestreado del archivo. */
    private static final String VERDE = "#42a165";
    private static final String TINTA = "#1d2b24";
    private static final String GRIS = "#6b7770";

    private final NotificacionProperties props;
    private final BonoContenido bono;

    /**
     * @param firmante quién emitió el bono, para el "Firmado electrónicamente por:" de la plantilla.
     * @return el cuerpo HTML, o {@code null} si no hay nada que mostrar distinto del texto plano.
     */
    public String armar(RecetaResponse receta, String firmante) {
        if (receta == null) {
            return null;
        }
        String linkCupon = bono.linkCupon(receta.codigo());
        String tienda = bono.tienda();
        StringBuilder h = new StringBuilder();

        h.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Tu bono profesional ").append(esc(receta.codigo())).append("</title></head>")
                // El fondo del body lo pinta el cliente de correo; el marco blanco lo pone la tabla.
                .append("<body style=\"margin:0;padding:0;background:#f2f4f2;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#f2f4f2;padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"width:600px;max-width:100%;background:#ffffff;border-radius:10px;")
                .append("font-family:Arial,Helvetica,sans-serif;color:").append(TINTA).append(";\">");

        // Logo
        String logo = imagen("bonosapp-logo.png");
        if (logo != null) {
            h.append("<tr><td style=\"padding:28px 32px 8px;\">")
                    .append("<img src=\"").append(logo).append("\" width=\"132\" alt=\"BonosApp\" ")
                    .append("style=\"display:block;border:0;width:132px;height:auto;\"></td></tr>");
        } else {
            h.append("<tr><td style=\"padding:28px 32px 8px;font-size:22px;font-weight:bold;\">BonosApp</td></tr>");
        }

        // Saludo + presentación
        h.append("<tr><td style=\"padding:8px 32px 0;font-size:15px;line-height:1.55;\">")
                .append("Estimada/o: <strong>").append(esc(nombrePaciente(receta))).append("</strong><br>")
                .append("Te dejamos tu bono profesional. El código es válido para <strong>una única compra</strong> ")
                .append("en el local informado. No es necesario imprimirlo: podés presentarlo desde el celular.")
                .append("</td></tr>");

        // Banda verde
        h.append("<tr><td style=\"padding:20px 32px 0;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                .append("<td align=\"center\" style=\"background:").append(VERDE).append(";color:#ffffff;")
                .append("font-size:15px;font-weight:bold;padding:12px;border-radius:4px;\">Bono Profesional")
                .append("</td></tr></table></td></tr>");

        // El bono
        h.append("<tr><td align=\"center\" style=\"padding:28px 32px 0;\">")
                .append("<div style=\"font-size:12px;letter-spacing:1.5px;color:").append(GRIS).append(";\">")
                .append("CÓDIGO DE DESCUENTO</div>")
                .append("<div style=\"font-size:34px;font-weight:bold;letter-spacing:2px;padding:6px 0 2px;\">")
                .append(esc(receta.codigo())).append("</div>")
                .append("<div style=\"font-size:17px;font-weight:bold;color:").append(VERDE).append(";\">")
                .append(pct(receta.descuentoPct())).append(" de descuento</div>");
        String producto = nombreProducto(receta);
        if (producto != null) {
            h.append("<div style=\"font-size:15px;padding-top:4px;\">").append(esc(producto)).append("</div>");
        }
        h.append("</td></tr>");

        // Cómo usarlo
        if (linkCupon != null) {
            h.append("<tr><td align=\"center\" style=\"padding:22px 32px 0;font-size:14px;line-height:1.5;color:")
                    .append(GRIS).append(";\">")
                    .append("Dale click al link y sumá el producto al carrito, y automáticamente estará aplicado ")
                    .append("tu bono <br>(No combinable con promociones activas)</td></tr>")
                    .append("<tr><td align=\"center\" style=\"padding:16px 32px 0;\">")
                    .append("<a href=\"").append(esc(linkCupon)).append("\" style=\"display:inline-block;")
                    .append("background:").append(VERDE).append(";color:#ffffff;text-decoration:none;")
                    .append("font-size:15px;font-weight:bold;padding:13px 26px;border-radius:6px;\">")
                    .append("Usar mi bono en la tienda</a></td></tr>");

            String urlProducto = urlDelProducto(receta);
            if (urlProducto != null) {
                h.append("<tr><td align=\"center\" style=\"padding:16px 32px 0;font-size:13px;color:")
                        .append(GRIS).append(";\">Después entrá al producto y sumalo al carrito:<br>")
                        .append("<a href=\"").append(esc(urlProducto)).append("\" style=\"color:")
                        .append(VERDE).append(";\">").append(esc(urlProducto)).append("</a></td></tr>");
            }
        } else {
            h.append("<tr><td align=\"center\" style=\"padding:20px 32px 0;font-size:14px;color:").append(GRIS)
                    .append(";\">Usá el código al finalizar tu compra en la tienda online.</td></tr>");
        }

        // Pie: dónde canjearlo + marca de la tienda
        h.append("<tr><td style=\"padding:28px 32px 0;\"><hr style=\"border:0;border-top:1px solid #e3e7e4;\"></td></tr>")
                .append("<tr><td align=\"center\" style=\"padding:16px 32px 0;font-size:13px;color:")
                .append(GRIS).append(";\">Podés canjear tu bono profesional en:<br>");
        if (tienda != null) {
            h.append("<a href=\"").append(esc(tienda)).append("\" style=\"color:").append(TINTA)
                    .append(";font-weight:bold;text-decoration:none;\">").append(esc(sinEsquema(tienda)))
                    .append("</a>");
        }
        h.append("</td></tr>");

        String tbc = imagen("tbc-qr.png");
        if (tbc != null) {
            h.append("<tr><td align=\"center\" style=\"padding:14px 32px 0;\">")
                    .append("<img src=\"").append(tbc).append("\" width=\"200\" alt=\"The B Company\" ")
                    .append("style=\"display:block;border:0;width:200px;height:auto;\"></td></tr>");
        }

        // Los tres datos del pie de la plantilla
        h.append("<tr><td style=\"padding:22px 32px 30px;font-size:12px;line-height:1.7;color:")
                .append(GRIS).append(";\">")
                .append("Fecha emisión: ").append(fechaEmision(receta)).append("<br>")
                .append("Fecha de vencimiento: ")
                .append(receta.venceAt() == null ? "—" : FECHA.format(receta.venceAt())).append("<br>");
        if (firmante != null && !firmante.isBlank()) {
            h.append("Firmado electrónicamente por: ").append(esc(firmante));
        }
        h.append("</td></tr></table></td></tr></table></body></html>");
        return h.toString();
    }

    /**
     * Las imágenes se sirven desde el propio dominio de la app ({@code /mail/…}, que salen de
     * {@code frontend/public}). Sin {@code APP_PUBLIC_URL} no hay forma de armar una URL absoluta
     * —y una relativa en un mail no apunta a ningún lado—, así que el mail sale sin ellas.
     */
    private String imagen(String archivo) {
        String base = props.appUrlNormalizada();
        return base.isBlank() ? null : base + "/mail/" + archivo;
    }

    private String fechaEmision(RecetaResponse receta) {
        return receta.emitidaAt() == null ? "—" : FECHA.format(receta.emitidaAt().atZone(AR).toLocalDate());
    }

    private String nombrePaciente(RecetaResponse receta) {
        if (receta.paciente() == null) {
            return "";
        }
        String nombre = receta.paciente().nombre() == null ? "" : receta.paciente().nombre();
        String apellido = receta.paciente().apellido() == null ? "" : receta.paciente().apellido();
        return (nombre + " " + apellido).trim();
    }

    /** Un solo producto: con dos no hay "el" producto, igual que en el resto del mensaje. */
    private String nombreProducto(RecetaResponse receta) {
        if (receta.items().size() != 1 || receta.items().get(0).producto() == null) {
            return null;
        }
        return receta.items().get(0).producto().nombre();
    }

    private String urlDelProducto(RecetaResponse receta) {
        if (receta.items().size() != 1 || receta.items().get(0).producto() == null) {
            return null;
        }
        return receta.items().get(0).producto().urlProducto();
    }

    private String sinEsquema(String url) {
        return url.replaceFirst("^https?://", "");
    }

    private String pct(BigDecimal descuento) {
        return descuento == null ? "—" : descuento.stripTrailingZeros().toPlainString() + "%";
    }

    /** El nombre del paciente y el del producto son datos cargados: no pueden romper el HTML. */
    private String esc(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
