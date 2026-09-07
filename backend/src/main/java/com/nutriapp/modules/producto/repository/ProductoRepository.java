package com.nutriapp.modules.producto.repository;

import com.nutriapp.modules.producto.entity.Producto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    /**
     * Buscador del emisor de recetas.
     *
     * <p><b>{@code q}</b> (texto libre, sin acentos) matchea nombre, descripción, SKU, código de barras
     * y <b>tags</b> del maestro. El resultado sale <b>rankeado</b> por dónde matcheó, en este orden:
     *
     * <ol start="0">
     *   <li>nombre — lo que la persona está buscando casi siempre;</li>
     *   <li>SKU — identificador exacto: si alguien lo tipea, sabe lo que quiere;</li>
     *   <li>código de barras — ídem, y suele venir de un escáner;</li>
     *   <li>descripción;</li>
     *   <li><b>tags</b>, siempre al final.</li>
     * </ol>
     *
     * <p>Los tags van últimos a propósito: son etiquetas de clasificación y muchas son genéricas
     * ("bienestar", "salud") — un producto puede tener 20 y matchear por una que no describe lo que
     * es. Coincide con el pedido de Gon (call 29:15): <i>"arriba los que tienen magnesio en el
     * nombre, más abajo los que lo tienen en el tag"</i>. Un OR plano mezclaría todo.
     *
     * <p>Los tags entran por {@code EXISTS} y no por un JOIN: con JOIN, un producto con 20 tags que
     * matchean saldría 20 veces y rompería la paginación.
     *
     * <p>Filtros opcionales (null/'' = sin filtro): marca (subrubro de Contabilium), departamento,
     * categoría, subcategoría y laboratorio (los cuatro del maestro), {@code tag} exacto,
     * {@code conStock}, y el rango de precio. Solo devuelve publicados (ver {@code PublicacionPolicy}).
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.deletedAt IS NULL
              AND p.publicado = true
              AND (:q IS NULL OR :q = ''
                   OR LOWER(FUNCTION('unaccent', CONCAT(p.nombre, ' ', COALESCE(p.descripcion, ''), ' ',
                        COALESCE(p.sku, ''), ' ', COALESCE(p.codigoBarras, ''))))
                      LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%')))
                   OR EXISTS (SELECT 1 FROM Producto pt JOIN pt.tags t
                              WHERE pt.id = p.id
                                AND t.tagNorm LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%')))))
              AND (:marca IS NULL OR :marca = ''
                   OR LOWER(FUNCTION('unaccent', p.marca)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :marca, '%'))))
              AND (:departamento IS NULL OR :departamento = ''
                   OR LOWER(FUNCTION('unaccent', p.departamento)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :departamento, '%'))))
              AND (:categoria IS NULL OR :categoria = ''
                   OR LOWER(FUNCTION('unaccent', p.categoria)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :categoria, '%'))))
              AND (:subcategoria IS NULL OR :subcategoria = ''
                   OR LOWER(FUNCTION('unaccent', p.subcategoria)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :subcategoria, '%'))))
              AND (:laboratorio IS NULL OR :laboratorio = ''
                   OR LOWER(FUNCTION('unaccent', p.laboratorio)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :laboratorio, '%'))))
              AND (:tag IS NULL OR :tag = ''
                   OR EXISTS (SELECT 1 FROM Producto px JOIN px.tags tx
                              WHERE px.id = p.id AND tx.tagNorm = LOWER(FUNCTION('unaccent', :tag))))
              AND (:conStock = FALSE OR (p.stock IS NOT NULL AND p.stock > 0))
              AND (:precioMin IS NULL OR p.precio >= :precioMin)
              AND (:precioMax IS NULL OR p.precio <= :precioMax)
            ORDER BY
              CASE
                WHEN :q IS NULL OR :q = '' THEN 0
                WHEN LOWER(FUNCTION('unaccent', p.nombre))
                     LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%'))) THEN 0
                WHEN LOWER(COALESCE(p.sku, ''))
                     LIKE LOWER(CONCAT('%', :q, '%')) THEN 1
                WHEN COALESCE(p.codigoBarras, '') LIKE CONCAT('%', :q, '%') THEN 2
                WHEN LOWER(FUNCTION('unaccent', COALESCE(p.descripcion, '')))
                     LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%'))) THEN 3
                ELSE 4
              END,
              p.nombre
            """)
    Page<Producto> search(@Param("q") String q,
                          @Param("marca") String marca,
                          @Param("departamento") String departamento,
                          @Param("categoria") String categoria,
                          @Param("subcategoria") String subcategoria,
                          @Param("laboratorio") String laboratorio,
                          @Param("tag") String tag,
                          @Param("conStock") boolean conStock,
                          @Param("precioMin") BigDecimal precioMin,
                          @Param("precioMax") BigDecimal precioMax,
                          Pageable pageable);

    /**
     * Buscador del <b>admin</b>: a diferencia del de recetas, ve también los despublicados (que es
     * justamente lo que necesita revisar) y puede filtrar por lo que no matcheó contra el maestro.
     *
     * <p>Se queda con el texto libre y la taxonomía; no repite los 10 filtros del emisor porque acá
     * la pregunta es otra: "¿qué quedó afuera y por qué?", no "¿qué le receto a esta paciente?".
     *
     * @param sinMaestro  true = sólo los que el maestro nunca tocó; false/null = todos.
     * @param publicado   true/false para filtrar por estado; null = ambos.
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.deletedAt IS NULL
              AND (:q IS NULL OR :q = ''
                   OR LOWER(FUNCTION('unaccent', CONCAT(p.nombre, ' ', COALESCE(p.descripcion, ''), ' ',
                        COALESCE(p.sku, ''), ' ', COALESCE(p.codigoBarras, ''))))
                      LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%'))))
              AND (:departamento IS NULL OR :departamento = '' OR p.departamento = :departamento)
              AND (:categoria IS NULL OR :categoria = '' OR p.categoria = :categoria)
              AND (:sinMaestro = FALSE OR p.maestroSyncedAt IS NULL)
              AND (:publicado IS NULL OR p.publicado = :publicado)
            ORDER BY p.nombre
            """)
    Page<Producto> searchAdmin(@Param("q") String q,
                               @Param("departamento") String departamento,
                               @Param("categoria") String categoria,
                               @Param("sinMaestro") boolean sinMaestro,
                               @Param("publicado") Boolean publicado,
                               Pageable pageable);

    long countByDeletedAtIsNull();

    long countByPublicadoAndDeletedAtIsNull(boolean publicado);

    /** En el ERP pero no en el Excel de TBC: se recetan sin categoría, laboratorio, imagen ni tags. */
    long countByMaestroSyncedAtIsNullAndDeletedAtIsNull();

    long countByBloqueadoMaestroAndDeletedAtIsNull(boolean bloqueadoMaestro);

    /** Conciliación del catálogo por SKU (clave natural TiendaNube ↔ Contabilium) — sync 2.9. */
    Optional<Producto> findBySkuAndDeletedAtIsNull(String sku);

    /** Ídem por lote: el mapeo contra TiendaNube resuelve una página entera en un solo query. */
    List<Producto> findBySkuInAndDeletedAtIsNull(Collection<String> skus);

    /**
     * ¿El mapeo contra TiendaNube corrió alguna vez? Apaga la regla de publicación por tienda mientras
     * el catálogo esté sin mapear, para que no quede entero despublicado (ver PublicacionPolicy).
     */
    boolean existsByTiendanubeProductIdIsNotNullAndDeletedAtIsNull();

    /** Todo el catálogo vivo: el mapeo recalcula la publicación sobre él tras escribir los ids. */
    List<Producto> findByDeletedAtIsNull();

    /** Publicados sin id de TiendaNube: no se les puede emitir cupón (ver CuponSyncService). */
    @Query("""
            SELECT COUNT(p) FROM Producto p
            WHERE p.deletedAt IS NULL AND p.publicado = true AND p.tiendanubeProductId IS NULL
            """)
    long countPublicadosSinMapear();

    /**
     * Carga en un solo query los productos que el import del maestro va a tocar, con los tags ya
     * traídos: comparar tag por tag con lazy loading serían 2225 queries extra por importación.
     */
    @Query("SELECT DISTINCT p FROM Producto p LEFT JOIN FETCH p.tags WHERE p.sku IN :skus AND p.deletedAt IS NULL")
    List<Producto> findParaImportacion(@Param("skus") Collection<String> skus);

    /** Última sincronización del catálogo (para el estado de integraciones, 2.7). */
    @Query("SELECT MAX(p.lastSyncedAt) FROM Producto p WHERE p.deletedAt IS NULL")
    Instant maxLastSyncedAt();

    /** Última importación del maestro reflejada en el catálogo (estado de integraciones). */
    @Query("SELECT MAX(p.maestroSyncedAt) FROM Producto p WHERE p.deletedAt IS NULL")
    Instant maxMaestroSyncedAt();

    @Query("SELECT DISTINCT p.marca FROM Producto p WHERE p.deletedAt IS NULL AND p.publicado = true AND p.marca IS NOT NULL ORDER BY p.marca")
    List<String> distinctMarcas();

    @Query("SELECT DISTINCT p.laboratorio FROM Producto p WHERE p.deletedAt IS NULL AND p.publicado = true AND p.laboratorio IS NOT NULL ORDER BY p.laboratorio")
    List<String> distinctLaboratorios();

    /** Precio mínimo y máximo de lo publicado, para los extremos del slider del buscador. */
    @Query("SELECT MIN(p.precio), MAX(p.precio) FROM Producto p WHERE p.deletedAt IS NULL AND p.publicado = true")
    Object[] rangoPrecios();

    /**
     * Las tres columnas de la taxonomía del maestro en una sola pasada, para armar el filtro en
     * cascada (departamento → categoría → subcategoría). Son ~150 combinaciones: no vale la pena
     * un round-trip por nivel, y sin cascada el dropdown de subcategoría tendría 142 opciones sueltas.
     */
    @Query("""
            SELECT DISTINCT p.departamento, p.categoria, p.subcategoria FROM Producto p
            WHERE p.deletedAt IS NULL AND p.publicado = true
              AND (p.departamento IS NOT NULL OR p.categoria IS NOT NULL OR p.subcategoria IS NOT NULL)
            ORDER BY p.departamento, p.categoria, p.subcategoria
            """)
    List<Object[]> taxonomia();
}
