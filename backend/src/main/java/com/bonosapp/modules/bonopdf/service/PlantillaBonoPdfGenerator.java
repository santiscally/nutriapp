package com.bonosapp.modules.bonopdf.service;

import com.bonosapp.modules.receta.dto.RecetaResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * El bono en PDF sobre la <b>plantilla que mandó el cliente</b> (F-20): la plantilla va como imagen
 * de fondo a página completa y encima se escriben los datos del bono.
 *
 * <p><b>Por qué una imagen y no un diseño rehecho:</b> la plantilla llegó como JPG con el logo, la
 * banda verde, el QR y la marca de TBC ya compuestos. Reconstruir eso a mano sería copiar un diseño
 * que ya existe, y cualquier retoque del cliente obligaría a rehacerlo — así, cambiar el diseño es
 * reemplazar un archivo.
 *
 * <p><b>Coordenadas:</b> la plantilla es A4 a 300 dpi (2480×3508 px). Las posiciones salen de medir
 * el archivo, no de estimarlas: cada valor se apoya en el rótulo impreso que le corresponde
 * ("Fecha emisión:", "Firmado electrónicamente por:"). Si el cliente manda una plantilla nueva con
 * los rótulos movidos, hay que volver a medir — están todas juntas en {@code Pos} justamente para eso.
 *
 * <p><b>Tipografía:</b> los valores van en Helvetica (base-14, no hace falta embeber nada). La
 * plantilla usa una tipografía redondeada propia, así que de cerca se nota que los valores son de
 * otra familia. Emparejarlo obliga a embeber la fuente real en el PDF; se puede hacer si el cliente
 * lo pide.
 */
@Slf4j
@Component
public class PlantillaBonoPdfGenerator implements BonoPdfGenerator {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    /** A4 en puntos. */
    private static final float ANCHO = 595.276f;
    private static final float ALTO = 841.89f;

    private static final String PLANTILLA = "bono/plantilla-bono.jpg";
    /** Píxeles de la plantilla → puntos del PDF. */
    private static final float ESCALA = ALTO / 3508f;

    /** Todo lo que depende del diseño de la plantilla, junto y medido sobre el archivo. */
    private static final class Pos {
        /** Después de "Estimada/o:". */
        static final float PACIENTE_X = px(266), PACIENTE_Y = alto(433);
        /** Después de cada rótulo del pie. */
        static final float EMISION_X = px(300), EMISION_Y = alto(3272);
        static final float VENCE_X = px(415), VENCE_Y = alto(3330);
        static final float FIRMA_X = px(537), FIRMA_Y = alto(3390);
        /** El hueco entre la banda verde (termina en 731) y el bloque de TBC (empieza en 2964). */
        static final float CUERPO_X = px(74);
        static final float CUERPO_TOPE = alto(900);
        static final float ANCHO_UTIL = px(2406) - px(74);

        static final int TAM_ROTULOS = 8;

        private static float px(int p) {
            return p * ESCALA;
        }

        private static float alto(int p) {
            return ALTO - p * ESCALA;
        }
    }

    private final byte[] plantilla;
    private final int plantillaAncho;
    private final int plantillaAlto;

    public PlantillaBonoPdfGenerator() {
        byte[] bytes = null;
        int[] dim = null;
        try (InputStream in = new ClassPathResource(PLANTILLA).getInputStream()) {
            bytes = in.readAllBytes();
            dim = medirJpeg(bytes);
        } catch (IOException | RuntimeException ex) {
            // Sin plantilla el bono sale igual, en blanco: el código del cupón es lo que la paciente
            // necesita, y un PDF sobrio es mejor que un mail sin adjunto.
            log.error("No se pudo leer la plantilla del bono ({}): los PDF salen sin fondo. {}",
                    PLANTILLA, ex.toString());
        }
        this.plantilla = bytes;
        this.plantillaAncho = dim == null ? 0 : dim[0];
        this.plantillaAlto = dim == null ? 0 : dim[1];
    }

    @Override
    public byte[] generar(RecetaResponse receta, String linkCupon, String firmante) {
        return armarPdf(contenido(receta, linkCupon, firmante));
    }

    // --- Lo que se escribe encima de la plantilla ---

    private String contenido(RecetaResponse receta, String linkCupon, String firmante) {
        Texto t = new Texto();

        // 1) Los tres rótulos que la plantilla deja abiertos, cada uno pegado al suyo.
        t.linea(Pos.PACIENTE_X, Pos.PACIENTE_Y, "F1", Pos.TAM_ROTULOS, nombrePaciente(receta));
        t.linea(Pos.EMISION_X, Pos.EMISION_Y, "F1", Pos.TAM_ROTULOS, fechaEmision(receta));
        t.linea(Pos.VENCE_X, Pos.VENCE_Y, "F1", Pos.TAM_ROTULOS,
                receta.venceAt() == null ? "—" : FECHA.format(receta.venceAt()));
        // Firma el profesional que emitió el bono, no la plataforma: el rótulo de la plantilla
        // pregunta quién lo firma, y quien lo emite es quien responde por él.
        t.linea(Pos.FIRMA_X, Pos.FIRMA_Y, "F1", Pos.TAM_ROTULOS, firmante);

        // 2) El cuerpo, en el hueco que la plantilla deja debajo de la banda verde. Centrado sobre
        //    el mismo ancho que la banda, para que no quede desalineado con el diseño.
        float centro = Pos.CUERPO_X + Pos.ANCHO_UTIL / 2;
        float y = Pos.CUERPO_TOPE;

        t.centrado(centro, y, "F1", 10, "CÓDIGO DE DESCUENTO");
        y -= 34;
        t.centrado(centro, y, "F2", 30, receta.codigo());
        y -= 30;
        t.centrado(centro, y, "F2", 13, pct(receta.descuentoPct()) + " de descuento");

        String producto = nombreProducto(receta);
        if (producto != null) {
            y -= 20;
            t.centrado(centro, y, "F1", 11, producto);
        }

        if (linkCupon != null) {
            y -= 40;
            t.centrado(centro, y, "F1", 9, "Dale click al link y sumá el producto al carrito, y");
            y -= 13;
            t.centrado(centro, y, "F1", 9, "automáticamente estará aplicado tu bono");
            y -= 13;
            t.centrado(centro, y, "F1", 9, "(No combinable con promociones activas)");
            y -= 18;
            t.centrado(centro, y, "F2", 9, linkCupon);

            String urlProducto = urlDelProducto(receta);
            if (urlProducto != null) {
                y -= 22;
                t.centrado(centro, y, "F1", 9, "Después entrá al producto y sumalo al carrito:");
                y -= 13;
                t.centrado(centro, y, "F2", 9, urlProducto);
            }
        }
        return t.build();
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

    /** Un solo producto: con dos no hay "el" producto, igual que en el mail. */
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

    private String pct(BigDecimal descuento) {
        return descuento == null ? "—" : descuento.stripTrailingZeros().toPlainString() + "%";
    }

    /** Acumula líneas de texto en el content stream de la página. */
    private static final class Texto {
        private final StringBuilder sb = new StringBuilder();

        void linea(float x, float y, String fuente, float tam, String texto) {
            if (texto == null || texto.isBlank()) {
                return;
            }
            sb.append("BT /").append(fuente).append(' ').append(num(tam)).append(" Tf 1 0 0 1 ")
                    .append(num(x)).append(' ').append(num(y)).append(" Tm (")
                    .append(escapar(texto)).append(") Tj ET\n");
        }

        /**
         * Centrado sobre {@code centro}. El ancho se estima con el promedio de Helvetica (0,5 em por
         * carácter, 0,58 en la negrita): alcanza para centrar un código o una URL, y evita tener que
         * cargar la tabla de anchos de la fuente para cuatro renglones.
         */
        void centrado(float centro, float y, String fuente, float tam, String texto) {
            if (texto == null || texto.isBlank()) {
                return;
            }
            float factor = "F2".equals(fuente) ? 0.58f : 0.5f;
            linea(centro - (texto.length() * tam * factor) / 2, y, fuente, tam, texto);
        }

        String build() {
            return sb.toString();
        }

        private static String num(float v) {
            return String.format(Locale.ROOT, "%.2f", v);
        }

        /** En un string de PDF, {@code \ ( )} son sintaxis y hay que escaparlos. */
        private static String escapar(String texto) {
            return texto.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }
    }

    // --- Estructura del archivo ---

    /**
     * Ensambla los objetos del PDF y la tabla xref. La xref indexa por <b>offset de byte</b> de cada
     * objeto, así que se escribe todo sobre el mismo stream y se anotan los offsets al vuelo.
     *
     * <p>La plantilla entra como XObject de imagen con filtro {@code DCTDecode}: un JPEG se embebe
     * tal cual, sin recomprimir ni decodificar, que es la razón de usar el JPG y no el PNG (ese
     * pediría implementar los filtros de PNG a mano).
     */
    private byte[] armarPdf(String contentStream) {
        boolean conFondo = plantilla != null && plantillaAncho > 0;
        // El `cm` escala la unidad a la página entera y `Do` dibuja la plantilla; después se
        // restaura la matriz con Q para que el texto use puntos y no la escala de la imagen.
        String fondo = conFondo
                ? "q " + String.format(Locale.ROOT, "%.3f 0 0 %.3f 0 0", ANCHO, ALTO) + " cm /Im0 Do Q\n"
                : "";
        byte[] stream = (fondo + contentStream).getBytes(WIN_ANSI);

        String recursos = "/Font<</F1 5 0 R/F2 6 0 R>>" + (conFondo ? "/XObject<</Im0 7 0 R>>" : "");
        List<String> objetos = new ArrayList<>(Arrays.asList(
                "<</Type/Catalog/Pages 2 0 R>>",
                "<</Type/Pages/Kids[3 0 R]/Count 1>>",
                "<</Type/Page/Parent 2 0 R/MediaBox[0 0 " + Texto.num(ANCHO) + " " + Texto.num(ALTO) + "]"
                        + "/Resources<<" + recursos + ">>/Contents 4 0 R>>",
                null, // 4: el content stream
                "<</Type/Font/Subtype/Type1/BaseFont/Helvetica/Encoding/WinAnsiEncoding>>",
                "<</Type/Font/Subtype/Type1/BaseFont/Helvetica-Bold/Encoding/WinAnsiEncoding>>"));
        if (conFondo) {
            objetos.add(null); // 7: la imagen, que también lleva bytes crudos
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        escribir(out, "%PDF-1.4\n");

        for (int i = 0; i < objetos.size(); i++) {
            offsets.add(out.size());
            int num = i + 1;
            if (num == 4) {
                escribir(out, "4 0 obj\n<</Length " + stream.length + ">>\nstream\n");
                out.writeBytes(stream);
                escribir(out, "\nendstream\nendobj\n");
            } else if (num == 7) {
                escribir(out, "7 0 obj\n<</Type/XObject/Subtype/Image"
                        + "/Width " + plantillaAncho + "/Height " + plantillaAlto
                        + "/ColorSpace/DeviceRGB/BitsPerComponent 8/Filter/DCTDecode"
                        + "/Length " + plantilla.length + ">>\nstream\n");
                out.writeBytes(plantilla);
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

    /**
     * Ancho y alto del JPEG, leídos del marcador SOF. El PDF necesita las dos medidas en el
     * diccionario de la imagen y no las deduce del stream.
     */
    private static int[] medirJpeg(byte[] jpeg) {
        int i = 2; // saltea SOI
        while (i + 9 < jpeg.length) {
            if ((jpeg[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int marcador = jpeg[i + 1] & 0xFF;
            int largo = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
            // SOF0..SOF15, salteando los que no describen un frame (DHT, JPG, DAC).
            if (marcador >= 0xC0 && marcador <= 0xCF && marcador != 0xC4 && marcador != 0xC8
                    && marcador != 0xCC) {
                int alto = ((jpeg[i + 5] & 0xFF) << 8) | (jpeg[i + 6] & 0xFF);
                int ancho = ((jpeg[i + 7] & 0xFF) << 8) | (jpeg[i + 8] & 0xFF);
                return new int[] {ancho, alto};
            }
            i += 2 + largo;
        }
        throw new IllegalStateException("La plantilla no parece un JPEG: no tiene marcador SOF");
    }

    /** La estructura del PDF es ASCII; sólo el content stream lleva CP1252. */
    private void escribir(ByteArrayOutputStream out, String texto) {
        out.writeBytes(texto.getBytes(StandardCharsets.ISO_8859_1));
    }
}
