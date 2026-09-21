package com.bonosapp.modules.bonopdf.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bonosapp.modules.paciente.dto.PacienteResponse;
import com.bonosapp.modules.producto.dto.ProductoResponse;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El PDF del bono lo abre la paciente en su teléfono: si el archivo está mal armado no falla en
 * el servidor, falla en el lector de PDF de ella. Por eso se verifica la estructura del archivo
 * (header, xref, trailer) y no sólo que salgan bytes.
 */
class PdfSimpleBonoGeneratorTest {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private final PdfSimpleBonoGenerator generator = new PdfSimpleBonoGenerator();

    private RecetaResponse receta() {
        ProductoResponse producto = new ProductoResponse(
                UUID.randomUUID(), "SKU-1", null, "Magnesio 300g", null, null,
                new BigDecimal("1000"), 5, null, null, null, null, null, null,
                List.of(), null, null, true, "CONTABILIUM");
        PacienteResponse paciente = new PacienteResponse(
                UUID.randomUUID(), "Ana", "Gómez", "ana@example.com", "+5491144443333",
                null, null, Instant.now());
        return new RecetaResponse(
                UUID.randomUUID(), "RX-3V737V", "PENDIENTE", paciente,
                List.of(new RecetaResponse.Item(producto, 1, null)),
                new BigDecimal("15.00"), Instant.parse("2026-08-04T12:00:00Z"),
                LocalDate.of(2026, 9, 3), "OK", null, null, null, null);
    }

    @Test
    void generaUnPdfConEstructuraValida() {
        byte[] pdf = generator.generar(receta(), "https://tienda.test/discount/RX-3V737V");
        String texto = new String(pdf, WIN_ANSI);

        assertThat(texto).startsWith("%PDF-1.4");
        assertThat(texto).endsWith("%%EOF\n");
        assertThat(texto).contains("/Type/Catalog").contains("xref").contains("trailer").contains("startxref");
    }

    /** El startxref tiene que apuntar al byte exacto donde arranca la tabla; si no, no abre. */
    @Test
    void elStartxrefApuntaAlaTablaXref() {
        byte[] pdf = generator.generar(receta(), null);
        String texto = new String(pdf, WIN_ANSI);

        int declarado = Integer.parseInt(
                texto.substring(texto.lastIndexOf("startxref\n") + "startxref\n".length(),
                        texto.lastIndexOf("\n%%EOF")).trim());

        assertThat(texto.indexOf("xref\n0 ")).isEqualTo(declarado);
    }

    @Test
    void traeLosDatosDelBono() {
        String texto = new String(generator.generar(receta(), "https://tienda.test/discount/RX-3V737V"), WIN_ANSI);

        assertThat(texto)
                .contains("RX-3V737V")
                .contains("Ana G")
                .contains("Magnesio 300g")
                .contains("15%")
                .contains("03/09/2026")
                .contains("https://tienda.test/discount/RX-3V737V");
    }

    /** Sin tienda configurada no hay link: el PDF sale igual, sin un renglón colgado. */
    @Test
    void sinLinkNoImprimeLaInstruccion() {
        String texto = new String(generator.generar(receta(), null), WIN_ANSI);

        assertThat(texto).doesNotContain("No combinable").doesNotContain("null");
    }
}
