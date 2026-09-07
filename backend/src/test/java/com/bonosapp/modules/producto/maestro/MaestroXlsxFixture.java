package com.bonosapp.modules.producto.maestro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/**
 * Fabrica xlsx con la forma del maestro de TBC. El archivo real no se commitea (trae costos y
 * márgenes del cliente — ver 07-maestro-articulos-y-catalogo.md §0), así que los tests replican sus
 * rarezas: columnas de sobra que bonosapp ignora, {@code "-"} como "no aplica", tags separados por
 * salto de línea y SKU numérico.
 */
final class MaestroXlsxFixture {

    private MaestroXlsxFixture() {
    }

    /** Encabezado con el orden y las columnas extra del archivo real (recortado). */
    static final String[] HEADERS = {
            "SKU", "ID CONTABILIUM", "CODIGO DE BARRAS", "DESCRIPCION", "TIPO", "COSTO",
            "DEPARTAMENTO", "CATEGORIA", "SUBCATEGORIA", "ELABORADOR / FABRICANTE", "MARCA",
            "ESTADO", "SUBESTADO", "LINK IMAGEN TIENDA NUBE", "DESCRIPCION WEB", "TAGS TIENDANUBE",
            "MARGEN %"
    };

    /** Fila completa, con los valores en las posiciones de {@link #HEADERS}. */
    static String[] fila(String sku, String idContabilium, String estado, String tags) {
        String[] f = new String[HEADERS.length];
        f[0] = sku;
        f[1] = idContabilium;
        f[2] = "7798349830060";
        f[3] = "ON-ROLL FLOW X 60 G";
        f[4] = "PRODUCTO";
        f[5] = "1830.83";
        f[6] = "SALUD Y BIENESTAR";
        f[7] = "TERAPIAS NATURALES";
        f[8] = "FLEBOTONICOS TOPICOS";
        f[9] = "JEIANELL";
        f[10] = "ON-ROLL";
        f[11] = estado;
        f[12] = "ACTIVO";
        f[13] = "https://dcdn-us.mitiendanube.com/stores/1/pic.jpg";
        f[14] = "Gel corporal rollon natural";
        f[15] = tags;
        f[16] = "0.83";
        return f;
    }

    /**
     * Varargs y no {@code List<String[]>} a propósito: {@code List.of(fila)} con un array de String
     * se resuelve como {@code List<String>} de sus celdas, no como una lista de una fila.
     */
    static byte[] build(String[]... filas) throws IOException {
        return buildCon(HEADERS, filas);
    }

    static byte[] buildCon(String[] headers, String[]... filasArr) throws IOException {
        List<String[]> filas = List.of(filasArr);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new Workbook(out, "test", "1.0")) {
            Worksheet ws = wb.newWorksheet("Maestro");
            for (int c = 0; c < headers.length; c++) {
                ws.value(0, c, headers[c]);
            }
            for (int r = 0; r < filas.size(); r++) {
                String[] fila = filas.get(r);
                for (int c = 0; c < fila.length; c++) {
                    if (fila[c] != null) {
                        ws.value(r + 1, c, fila[c]);
                    }
                }
            }
            ws.finish();
        }
        return out.toByteArray();
    }
}
