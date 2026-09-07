package com.bonosapp.modules.producto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tag del maestro de artículos ("TAGS TIENDANUBE"), la vía por la que se busca por propiedad o
 * principio activo — el maestro no tiene esos campos y los tags sí ("magnesio" está en 39 artículos).
 *
 * <p>Se guardan dos formas de lo mismo: {@code tag} tal como lo escribió TBC (para mostrarlo) y
 * {@code tagNorm} sin acentos, en minúsculas y sin espacios de más (para indexar y deduplicar). En el
 * Excel conviven 'salud' (436 veces) y 'Salud' (51): sin normalizar serían dos tags distintos.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductoTag {

    /** El maestro corta en 499 chars por celda; el tag individual es mucho más corto. */
    public static final int MAX_LEN = 120;

    @Column(name = "tag", nullable = false, length = MAX_LEN)
    private String tag;

    @Column(name = "tag_norm", nullable = false, length = MAX_LEN)
    private String tagNorm;

    private ProductoTag(String tag, String tagNorm) {
        this.tag = tag;
        this.tagNorm = tagNorm;
    }

    /** Devuelve null si el texto no deja nada utilizable (celda vacía, guiones, solo espacios). */
    public static ProductoTag of(String raw) {
        if (raw == null) {
            return null;
        }
        String limpio = raw.trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty() || "-".equals(limpio)) {
            return null;
        }
        String norm = normalizar(limpio);
        if (norm.isEmpty()) {
            return null;
        }
        return new ProductoTag(recortar(limpio), recortar(norm));
    }

    /** Minúsculas + sin diacríticos. Misma normalización que usa el buscador sobre el término tipeado. */
    public static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String base = Normalizer.normalize(s.trim().replaceAll("\\s+", " "), Normalizer.Form.NFD);
        return base.replaceAll("\\p{M}+", "").toLowerCase();
    }

    private static String recortar(String s) {
        return s.length() <= MAX_LEN ? s : s.substring(0, MAX_LEN);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProductoTag other && Objects.equals(tagNorm, other.tagNorm);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tagNorm);
    }
}
