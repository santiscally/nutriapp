package com.bonosapp.modules.producto.maestro;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Optional;

/**
 * Las columnas del maestro de artículos de TBC que bonosapp lee. Son 9 de las 126 que tiene la
 * planilla (la hoja "Columnas usables en Nutriapp" del archivo de Gon), más `ID CONTABILIUM` que
 * usamos para verificar el cruce.
 *
 * <p><b>Se busca por nombre de columna, nunca por posición.</b> Son 126 columnas de una planilla viva
 * que el cliente edita todos los días: si mañana insertan una columna al medio, un parser posicional
 * escribiría marcas en el campo de categoría sin que nadie se entere. Por nombre, o encuentra la
 * columna o falla ruidosamente.
 *
 * <p>Todo lo demás de la planilla — costos, márgenes, comisiones, precios por proveedor — se ignora
 * deliberadamente: no entra a la base de bonosapp (ver 07-maestro-articulos-y-catalogo.md §0).
 */
public enum MaestroColumna {

    SKU("SKU", true),
    /** Verificación cruzada del match; si el Excel no la trae, se importa igual. */
    ID_CONTABILIUM("ID CONTABILIUM", false),
    DEPARTAMENTO("DEPARTAMENTO", true),
    CATEGORIA("CATEGORIA", true),
    SUBCATEGORIA("SUBCATEGORIA", true),
    ELABORADOR("ELABORADOR / FABRICANTE", true),
    ESTADO("ESTADO", true),
    IMAGEN_URL("LINK IMAGEN TIENDA NUBE", true),
    DESCRIPCION_WEB("DESCRIPCION WEB", true),
    TAGS("TAGS TIENDANUBE", true);

    private final String header;
    private final boolean obligatoria;

    MaestroColumna(String header, boolean obligatoria) {
        this.header = header;
        this.obligatoria = obligatoria;
    }

    /** Texto exacto del encabezado en la hoja "Maestro", tal como lo manda TBC. */
    public String header() {
        return header;
    }

    /** Si falta, el import se rechaza entero en vez de dejar el campo en null para todo el catálogo. */
    public boolean obligatoria() {
        return obligatoria;
    }

    /** Match tolerante a mayúsculas, acentos y espacios de más — no a nombres distintos. */
    public boolean coincideCon(String headerLeido) {
        return normalizar(header).equals(normalizar(headerLeido));
    }

    public static Optional<MaestroColumna> porHeader(String headerLeido) {
        return Arrays.stream(values()).filter(c -> c.coincideCon(headerLeido)).findFirst();
    }

    static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String base = Normalizer.normalize(s.trim().replaceAll("\\s+", " "), Normalizer.Form.NFD);
        return base.replaceAll("\\p{M}+", "").toUpperCase();
    }
}
