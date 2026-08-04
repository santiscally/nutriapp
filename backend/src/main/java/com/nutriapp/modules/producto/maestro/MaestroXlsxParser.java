package com.nutriapp.modules.producto.maestro;

import com.nutriapp.common.error.UnprocessableException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.stereotype.Component;

/**
 * Lee el .xlsx del maestro de artículos de TBC y devuelve solo las filas y columnas que nutriapp usa.
 *
 * <p>Streaming (fastexcel): el archivo real son 2225 filas x 126 columnas y no queremos el DOM entero
 * en memoria por 9 columnas. No escribe nada: el que decide qué hacer con las filas es
 * {@link MaestroImportService}.
 */
@Slf4j
@Component
public class MaestroXlsxParser {

    /** Nombre de la hoja de datos. Si no está, se usa la primera (por si la renombran). */
    private static final String HOJA = "Maestro";

    /**
     * @throws UnprocessableException si el archivo no es un xlsx legible o si falta alguna columna
     *                                obligatoria — con la lista, para que el admin sepa qué arreglar.
     */
    public List<MaestroFila> parsear(InputStream in) {
        try (ReadableWorkbook wb = new ReadableWorkbook(in)) {
            Sheet hoja = wb.findSheet(HOJA).orElseGet(() -> wb.getFirstSheet());
            try (Stream<Row> rows = hoja.openStream()) {
                return leer(rows.iterator());
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof UnprocessableException ue) {
                throw ue; // ya trae el mensaje accionable: no lo pisamos con el genérico
            }
            // Un .xlsx es un zip: subir un .csv renombrado, un .xls viejo o un archivo cortado falla
            // acá con IOException. Es un error del usuario y tiene arreglo, así que sale como 422 con
            // instrucciones y no como "error interno".
            log.warn("[maestro] archivo ilegible: {}", ex.toString());
            throw new UnprocessableException("El archivo no es un Excel (.xlsx) válido o está dañado.");
        }
    }

    private List<MaestroFila> leer(java.util.Iterator<Row> it) {
        if (!it.hasNext()) {
            throw new UnprocessableException("La hoja del maestro está vacía.");
        }
        Map<MaestroColumna, Integer> idx = mapearEncabezado(it.next());

        List<MaestroFila> filas = new ArrayList<>();
        while (it.hasNext()) {
            Row row = it.next();
            String sku = texto(row, idx.get(MaestroColumna.SKU));
            if (sku == null) {
                continue; // fila vacía o de relleno: sin SKU no hay nada que cruzar
            }
            filas.add(new MaestroFila(
                    row.getRowNum(),
                    sku,
                    entero(texto(row, idx.get(MaestroColumna.ID_CONTABILIUM))),
                    texto(row, idx.get(MaestroColumna.DEPARTAMENTO)),
                    texto(row, idx.get(MaestroColumna.CATEGORIA)),
                    texto(row, idx.get(MaestroColumna.SUBCATEGORIA)),
                    texto(row, idx.get(MaestroColumna.ELABORADOR)),
                    esBloqueado(texto(row, idx.get(MaestroColumna.ESTADO))),
                    texto(row, idx.get(MaestroColumna.IMAGEN_URL)),
                    texto(row, idx.get(MaestroColumna.DESCRIPCION_WEB)),
                    MaestroFila.parsearTags(texto(row, idx.get(MaestroColumna.TAGS)))));
        }
        return filas;
    }

    /** Ubica cada columna por su nombre. Si una aparece repetida, gana la primera. */
    private Map<MaestroColumna, Integer> mapearEncabezado(Row header) {
        Map<MaestroColumna, Integer> idx = new EnumMap<>(MaestroColumna.class);
        for (int i = 0; i < header.getCellCount(); i++) {
            String h = texto(header, i);
            if (h == null) {
                continue;
            }
            int posicion = i;
            MaestroColumna.porHeader(h).ifPresent(col -> idx.putIfAbsent(col, posicion));
        }
        List<String> faltan = java.util.Arrays.stream(MaestroColumna.values())
                .filter(MaestroColumna::obligatoria)
                .filter(c -> !idx.containsKey(c))
                .map(MaestroColumna::header)
                .toList();
        if (!faltan.isEmpty()) {
            throw new UnprocessableException(
                    "El archivo no tiene estas columnas: " + String.join(", ", faltan)
                            + ". Revisá que sea el maestro de artículos y que no le hayan cambiado los títulos.");
        }
        return idx;
    }

    /** "BLOQUEADO" (en cualquier capitalización) bloquea; cualquier otra cosa, no. */
    private static boolean esBloqueado(String estado) {
        return estado != null && "BLOQUEADO".equalsIgnoreCase(estado.trim());
    }

    private static Long entero(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Texto de una celda, o null si está vacía o es un placeholder de la planilla.
     *
     * <p>Dos detalles del archivo real: TBC usa {@code "-"} como "no aplica" (no es un dato), y las
     * columnas numéricas —SKU e ID CONTABILIUM— pueden venir como {@code 3.0} según cómo se haya
     * guardado la celda; el SKU se compara como string contra la DB, así que {@code 3.0} no matchearía.
     */
    private static String texto(Row row, Integer indice) {
        if (indice == null || indice < 0 || indice >= row.getCellCount()) {
            return null;
        }
        Optional<Cell> celda = Optional.ofNullable(row.getCell(indice));
        if (celda.isEmpty()) {
            return null;
        }
        String raw = celda.get().getText();
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || "-".equals(s)) {
            return null;
        }
        return s.matches("-?\\d+\\.0+") ? s.substring(0, s.indexOf('.')) : s;
    }
}
