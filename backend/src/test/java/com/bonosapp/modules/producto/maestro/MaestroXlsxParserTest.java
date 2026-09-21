package com.bonosapp.modules.producto.maestro;

import static com.bonosapp.modules.producto.maestro.MaestroXlsxFixture.build;
import static com.bonosapp.modules.producto.maestro.MaestroXlsxFixture.buildCon;
import static com.bonosapp.modules.producto.maestro.MaestroXlsxFixture.fila;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bonosapp.common.error.UnprocessableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaestroXlsxParserTest {

    private final MaestroXlsxParser parser = new MaestroXlsxParser();

    private List<MaestroFila> parsear(byte[] archivo) {
        return parser.parsear(new ByteArrayInputStream(archivo));
    }

    @Test
    void leeLasNueveColumnasYDescartaElResto() throws IOException {
        List<MaestroFila> filas = parsear(build(fila("3", "9418829", "ACTIVO", "Bienestar\nSalud")));

        assertThat(filas).hasSize(1);
        MaestroFila f = filas.get(0);
        assertThat(f.sku()).isEqualTo("3");
        assertThat(f.idContabilium()).isEqualTo(9418829L);
        assertThat(f.departamento()).isEqualTo("SALUD Y BIENESTAR");
        assertThat(f.categoria()).isEqualTo("TERAPIAS NATURALES");
        assertThat(f.subcategoria()).isEqualTo("FLEBOTONICOS TOPICOS");
        assertThat(f.laboratorio()).isEqualTo("JEIANELL");
        assertThat(f.bloqueado()).isFalse();
        assertThat(f.imagenUrl()).startsWith("https://dcdn-us.mitiendanube.com");
        assertThat(f.descripcionWeb()).isEqualTo("Gel corporal rollon natural");
        assertThat(f.tags()).containsExactly("Bienestar", "Salud");
    }

    @Test
    void estadoBloqueadoMarcaLaFila() throws IOException {
        assertThat(parsear(build(fila("40", "12733947", "BLOQUEADO", null))).get(0).bloqueado()).isTrue();
    }

    @Test
    void tagsSeSeparanPorSaltoDeLineaYSeDeduplicanIgnorandoMayusculas() throws IOException {
        // En el archivo real conviven 'salud' (436 veces) y 'Salud' (51): es el mismo tag.
        byte[] archivo = build(fila("3", "9418829", "ACTIVO", "salud\nSalud\n  bienestar  \n\nSALUD"));

        assertThat(parsear(archivo).get(0).tags()).containsExactly("salud", "bienestar");
    }

    @Test
    void elGuionEsNoAplica_noUnDato() throws IOException {
        String[] f = fila("50", "1", "ACTIVO", "-");
        f[13] = "-";
        f[14] = "-";

        MaestroFila leida = parsear(build(f)).get(0);

        assertThat(leida.imagenUrl()).isNull();
        assertThat(leida.descripcionWeb()).isNull();
        assertThat(leida.tags()).isEmpty();
    }

    @Test
    void filasSinSkuSeIgnoran() throws IOException {
        byte[] archivo = build(
                fila("3", "9418829", "ACTIVO", null),
                new String[MaestroXlsxFixture.HEADERS.length],
                fila("18", "9418962", "ACTIVO", null));

        assertThat(parsear(archivo)).extracting(MaestroFila::sku).containsExactly("3", "18");
    }

    @Test
    void columnasEnOtroOrden_seUbicanIgualPorNombre() throws IOException {
        // El parser busca por nombre justamente para sobrevivir a esto: si fuera posicional,
        // escribiría el laboratorio en el campo de categoría sin avisar.
        String[] headers = {"TAGS TIENDANUBE", "ESTADO", "SUBCATEGORIA", "CATEGORIA", "DEPARTAMENTO",
                "elaborador / fabricante", "DESCRIPCION WEB", "LINK IMAGEN TIENDA NUBE", "SKU"};
        byte[] archivo = buildCon(headers,
                new String[]{"magnesio", "ACTIVO", "SUB", "CAT", "DEP", "LAB", "desc", "http://x/y.jpg", "77"});

        MaestroFila f = parsear(archivo).get(0);

        assertThat(f.sku()).isEqualTo("77");
        assertThat(f.departamento()).isEqualTo("DEP");
        assertThat(f.categoria()).isEqualTo("CAT");
        assertThat(f.subcategoria()).isEqualTo("SUB");
        assertThat(f.laboratorio()).isEqualTo("LAB");
        assertThat(f.tags()).containsExactly("magnesio");
        assertThat(f.idContabilium()).isNull(); // la columna es opcional
    }

    @Test
    void faltaUnaColumnaObligatoria_diceCual() throws IOException {
        String[] headers = {"SKU", "DEPARTAMENTO", "CATEGORIA", "ESTADO"};
        byte[] archivo = buildCon(headers, new String[]{"3", "DEP", "CAT", "ACTIVO"});

        assertThatThrownBy(() -> parsear(archivo))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("SUBCATEGORIA")
                .hasMessageContaining("ELABORADOR / FABRICANTE")
                .hasMessageContaining("TAGS TIENDANUBE");
    }

    /** Las 11 columnas del maestro nuevo (S-01), que ya no trae ID CONTABILIUM. */
    @Test
    void maestroNuevo_leeDescuentoYEstadoBonosapp() throws IOException {
        String[] headers = {"SKU", "DEPARTAMENTO", "CATEGORIA", "SUBCATEGORIA",
                "ELABORADOR / FABRICANTE", "ESTADO", "LINK IMAGEN TIENDA NUBE", "DESCRIPCION WEB",
                "TAGS TIENDANUBE", "DESCUENTO %", "ESTADO BONOSAPP"};
        byte[] archivo = buildCon(headers,
                new String[]{"3", "DEP", "CAT", "SUB", "LAB", "BLOQUEADO", "http://x/y.jpg", "desc",
                        "magnesio", "0.55", "SI"});

        MaestroFila f = parsear(archivo).get(0);

        assertThat(f.descuentoPct()).isEqualByComparingTo("55.00");
        assertThat(f.estadoBonosapp()).isTrue();
        assertThat(f.bloqueado()).isTrue();
        assertThat(f.idContabilium()).isNull();
    }

    /** Sin las columnas nuevas no se inventa nada: null es "el maestro no dijo", no "0" ni "NO". */
    @Test
    void maestroViejo_dejaDescuentoYEstadoBonosappEnNull() throws IOException {
        MaestroFila f = parsear(build(fila("3", "9418829", "ACTIVO", null))).get(0);

        assertThat(f.descuentoPct()).isNull();
        assertThat(f.estadoBonosapp()).isNull();
    }

    /**
     * El archivo de TBC trae el descuento como fracción sin formato de porcentaje. Un "1" tipeado a
     * mano tiene que valer 1 %, nunca 100 %: equivocarse para el otro lado regala el producto.
     */
    @Test
    void descuento_aceptaFraccionYPorcentaje() {
        assertThat(MaestroXlsxParser.descuento("0.2")).isEqualByComparingTo("20.00");
        assertThat(MaestroXlsxParser.descuento("0,55")).isEqualByComparingTo("55.00");
        assertThat(MaestroXlsxParser.descuento("1")).isEqualByComparingTo("1.00");
        assertThat(MaestroXlsxParser.descuento("20")).isEqualByComparingTo("20.00");
        assertThat(MaestroXlsxParser.descuento("20%")).isEqualByComparingTo("20.00");
        assertThat(MaestroXlsxParser.descuento("101")).isNull();
        assertThat(MaestroXlsxParser.descuento("-3")).isNull();
        assertThat(MaestroXlsxParser.descuento("ninguno")).isNull();
        assertThat(MaestroXlsxParser.descuento(null)).isNull();
    }

    @Test
    void estadoBonosapp_soloSiYNoDecidenAlgo() {
        assertThat(MaestroXlsxParser.siNo("SI")).isTrue();
        assertThat(MaestroXlsxParser.siNo("Sí")).isTrue();
        assertThat(MaestroXlsxParser.siNo("no")).isFalse();
        assertThat(MaestroXlsxParser.siNo("tal vez")).isNull();
        assertThat(MaestroXlsxParser.siNo(null)).isNull();
    }

    @Test
    void archivoQueNoEsXlsx_daError() {
        assertThatThrownBy(() -> parsear("no soy un excel".getBytes()))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("Excel");
    }
}
