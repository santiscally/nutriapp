package com.nutriapp.modules.producto.repository;

import com.nutriapp.modules.producto.entity.Producto;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    /**
     * Buscador del emisor de recetas. `q` (texto libre, unaccent) sobre nombre+descripcion+sku;
     * marca/laboratorio/principioActivo/presentacion como filtros opcionales (unaccent, contains).
     * Todos null/'' → todos los publicados no borrados.
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.deletedAt IS NULL
              AND p.publicado = true
              AND (:q IS NULL OR :q = ''
                   OR LOWER(FUNCTION('unaccent', CONCAT(p.nombre, ' ', COALESCE(p.descripcion, ''), ' ', COALESCE(p.sku, ''))))
                      LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :q, '%'))))
              AND (:marca IS NULL OR :marca = ''
                   OR LOWER(FUNCTION('unaccent', p.marca)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :marca, '%'))))
              AND (:laboratorio IS NULL OR :laboratorio = ''
                   OR LOWER(FUNCTION('unaccent', p.laboratorio)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :laboratorio, '%'))))
              AND (:principioActivo IS NULL OR :principioActivo = ''
                   OR LOWER(FUNCTION('unaccent', p.principioActivo)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :principioActivo, '%'))))
              AND (:presentacion IS NULL OR :presentacion = ''
                   OR LOWER(FUNCTION('unaccent', p.presentacion)) LIKE LOWER(FUNCTION('unaccent', CONCAT('%', :presentacion, '%'))))
            """)
    Page<Producto> search(@Param("q") String q,
                          @Param("marca") String marca,
                          @Param("laboratorio") String laboratorio,
                          @Param("principioActivo") String principioActivo,
                          @Param("presentacion") String presentacion,
                          Pageable pageable);

    @Query("SELECT DISTINCT p.marca FROM Producto p WHERE p.deletedAt IS NULL AND p.marca IS NOT NULL ORDER BY p.marca")
    List<String> distinctMarcas();

    @Query("SELECT DISTINCT p.laboratorio FROM Producto p WHERE p.deletedAt IS NULL AND p.laboratorio IS NOT NULL ORDER BY p.laboratorio")
    List<String> distinctLaboratorios();

    @Query("SELECT DISTINCT p.presentacion FROM Producto p WHERE p.deletedAt IS NULL AND p.presentacion IS NOT NULL ORDER BY p.presentacion")
    List<String> distinctPresentaciones();
}
