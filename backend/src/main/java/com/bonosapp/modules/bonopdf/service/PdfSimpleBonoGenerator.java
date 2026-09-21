package com.bonosapp.modules.bonopdf.service;

import com.bonosapp.modules.receta.dto.RecetaResponse;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * PDF de una carilla con los datos del bono, escrito a mano sobre la estructura de un PDF 1.4.
 *
 * <p><b>Por qué a mano:</b> el diseño definitivo lo manda el cliente (F-20) y hasta entonces no
 * vale la pena discutir una dependencia nueva en el {@code pom.xml} — que además es zona de Santi.
 * Esto usa sólo las fuentes base-14 (Helvetica), que todo lector de PDF tiene, así que no hay que
 * embeber nada. Cuando llegue la plantilla se reemplaza la implementación del port, no el flujo.
 *
 * <p><b>Acentos:</b> las base-14 se direccionan con {@code WinAnsiEncoding}, así que el texto se
 * serializa en CP1252 y no en UTF-8. Un "ó" en UTF-8 saldría como dos caracteres raros.
 */
@Component
public class PdfSimpleBonoGenerator implements BonoPdfGenerator {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    /** A4 en puntos. */
    private static final int ANCHO = 595;
    private static final int ALTO = 842;
    private static final int MARGEN = 64;

    @Override
    public byte[] generar(RecetaResponse receta, String linkCupon) {
        return armarPdf(contenido(receta, linkCupon));
    }

    // --- Contenido (lo que va a cambiar cuando llegue la plantilla del cliente) ---

    private String contenido(RecetaResponse receta, String linkCupon) {
        Texto t = new Texto();
        int y = ALTO - MARGEN;

        t.linea(MARGEN, y, "F2", 22, "BONO PROFESIONAL");
        y -= 14;
        t.linea(MARGEN, y, "F1", 10, "BonosApp");

        y -= 46;
        t.linea(MARGEN, y, "F1", 11, "Código de descuento");
        y -= 30;
        t.linea(MARGEN, y, "F2", 28, receta.codigo());

        y -= 44;
        t.linea(MARGEN, y, "F2", 13, "Descuento: " + pct(receta.descuentoPct()));
        y -= 22;
        t.linea(MARGEN, y, "F1", 12, "Válido hasta: "
                + (receta.venceAt() == null ? "—" : FECHA.format(receta.venceAt())));
        y -= 18;
        t.linea(MARGEN, y, "F1", 12, "Emitido el: " + (receta.emitidaAt() == null
                ? "—" : FECHA.format(receta.emitidaAt().atZone(AR).toLocalDate())));

        if (receta.paciente() != null) {
            y -= 32;
            t.linea(MARGEN, y, "F1", 12, "Para: " + nombrePaciente(receta));
        }

        for (RecetaResponse.Item item : receta.items()) {
            if (item.producto() == null) {
                continue;
            }
            y -= 20;
            t.linea(MARGEN, y, "F1", 12, "Producto: " + item.producto().nombre()
                    + (item.cantidad() > 1 ? " (x" + item.cantidad() + ")" : ""));
        }

        if (linkCupon != null) {
            y -= 40;
            t.linea(MARGEN, y, "F1", 11, "Dale click al link y sumá el producto al carrito, y");
            y -= 16;
            t.linea(MARGEN, y, "F1", 11, "automáticamente estará aplicado tu bono");
            y -= 16;
            t.linea(MARGEN, y, "F1", 11, "(No combinable con promociones activas):");
            y -= 20;
            t.linea(MARGEN, y, "F2", 11, linkCupon);
        }

        t.linea(MARGEN, MARGEN, "F1", 9,
                "Este bono es personal e intransferible y se puede usar una sola vez.");
        return t.build();
    }

    private String nombrePaciente(RecetaResponse receta) {
        String nombre = receta.paciente().nombre() == null ? "" : receta.paciente().nombre();
        String apellido = receta.paciente().apellido() == null ? "" : receta.paciente().apellido();
        return (nombre + " " + apellido).trim();
    }

    private String pct(BigDecimal descuento) {
        return descuento == null ? "—" : descuento.stripTrailingZeros().toPlainString() + "%";
    }

    /** Acumula líneas de texto en el content stream de la página. */
    private static final class Texto {
        private final StringBuilder sb = new StringBuilder();

        void linea(int x, int y, String fuente, int tam, String texto) {
            sb.append("BT /").append(fuente).append(' ').append(tam).append(" Tf 1 0 0 1 ")
                    .append(x).append(' ').append(y).append(" Tm (")
                    .append(escapar(texto)).append(") Tj ET\n");
        }

        String build() {
            return sb.toString();
        }

        /** En un string de PDF, {@code \ ( )} son sintaxis y hay que escaparlos. */
        private static String escapar(String texto) {
            return texto.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }
    }

    // --- Estructura del archivo (esto no cambia con el diseño) ---

    /**
     * Ensambla los 6 objetos del PDF y la tabla xref. La xref indexa por <b>offset de byte</b> de
     * cada objeto, así que se escribe todo sobre el mismo stream y se anotan los offsets al vuelo:
     * calcularlos después obligaría a serializar dos veces y a que las dos pasadas coincidan.
     */
    private byte[] armarPdf(String contentStream) {
        byte[] stream = contentStream.getBytes(WIN_ANSI);
        // asList y no List.of: el objeto 4 va como null (son bytes, no texto) y List.of no acepta nulls.
        List<String> objetos = Arrays.asList(
                "<</Type/Catalog/Pages 2 0 R>>",
                "<</Type/Pages/Kids[3 0 R]/Count 1>>",
                "<</Type/Page/Parent 2 0 R/MediaBox[0 0 " + ANCHO + " " + ALTO + "]"
                        + "/Resources<</Font<</F1 5 0 R/F2 6 0 R>>>>/Contents 4 0 R>>",
                null, // 4: el content stream, que lleva bytes crudos
                "<</Type/Font/Subtype/Type1/BaseFont/Helvetica/Encoding/WinAnsiEncoding>>",
                "<</Type/Font/Subtype/Type1/BaseFont/Helvetica-Bold/Encoding/WinAnsiEncoding>>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        escribir(out, "%PDF-1.4\n");

        for (int i = 0; i < objetos.size(); i++) {
            offsets.add(out.size());
            int num = i + 1;
            if (objetos.get(i) == null) {
                escribir(out, num + " 0 obj\n<</Length " + stream.length + ">>\nstream\n");
                out.writeBytes(stream);
                escribir(out, "\nendstream\nendobj\n");
            } else {
                escribir(out, num + " 0 obj\n" + objetos.get(i) + "\nendobj\n");
            }
        }

        int xref = out.size();
        escribir(out, "xref\n0 " + (objetos.size() + 1) + "\n");
        escribir(out, "0000000000 65535 f \n");
        // Cada entrada de la xref mide exactamente 20 bytes: 10 de offset + " 00000 n \n".
        for (Integer offset : offsets) {
            escribir(out, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        escribir(out, "trailer\n<</Size " + (objetos.size() + 1) + "/Root 1 0 R>>\n"
                + "startxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    /** La estructura del PDF es ASCII; sólo el content stream lleva CP1252. */
    private void escribir(ByteArrayOutputStream out, String texto) {
        out.writeBytes(texto.getBytes(StandardCharsets.ISO_8859_1));
    }
}
