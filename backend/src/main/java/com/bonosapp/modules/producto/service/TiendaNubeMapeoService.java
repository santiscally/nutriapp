package com.bonosapp.modules.producto.service;

import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.health.IntegrationHealthRegistry;
import com.bonosapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient.Product;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient.ProductPage;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient.Variant;
import com.bonosapp.modules.producto.dto.MapeoTiendaNubeResponse;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Escribe en el catálogo local los ids de TiendaNube ({@code tiendanube_product_id} /
 * {@code tiendanube_variant_id}) conciliando por SKU, la única clave que comparten el ERP, el
 * maestro de TBC y la tienda.
 *
 * <p>Es requisito para emitir recetas en live: {@code CuponSyncService} arma el {@code products[]}
 * del cupón con esos ids, y si están en null el cupón queda sin restricción — el descuento aplicaría
 * a toda la tienda.
 *
 * <p>Manual a demanda, como el import del maestro: el catálogo de la tienda cambia poco y el admin
 * necesita ver qué no matcheó. No es {@code @Transactional}: cada página se guarda en su propia
 * transacción corta para no retener el pool durante los round-trips HTTP (lección GIA).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TiendaNubeMapeoService {

    /** Máximo que acepta la API; el catálogo real son ~2266 productos, unas 12 páginas. */
    private static final int PER_PAGE = 200;

    /** Backstop: si la API nunca dijera "no hay next", el barrido igual termina. */
    private static final int MAX_PAGINAS = 200;

    private static final int MUESTRA_SIN_MATCH = 50;

    private final ProductoRepository repo;
    private final TiendaNubeClient tiendaNubeClient;
    private final IntegrationHealthRegistry health;
    private final PublicacionPolicy publicacionPolicy;

    public MapeoTiendaNubeResponse mapear() {
        Instant mapeadoAt = Instant.now();
        int revisados = 0;
        int mapeados = 0;
        int yaMapeados = 0;
        int sinSku = 0;
        int sinMatch = 0;
        List<String> skusSinMatch = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_PAGINAS; page++) {
                ProductPage tnPage = tiendaNubeClient.listProducts(page, PER_PAGE);
                Map<String, Variant> porSku = new LinkedHashMap<>();
                for (Product p : tnPage.items()) {
                    for (Variant v : p.variants()) {
                        revisados++;
                        if (v.sku() == null || v.sku().isBlank()) {
                            sinSku++;
                            continue;
                        }
                        porSku.put(v.sku().trim(), v);
                    }
                }
                Map<String, Product> productoPorSku = productoPorSku(tnPage.items());
                List<Producto> locales = porSku.isEmpty()
                        ? List.of()
                        : repo.findBySkuInAndDeletedAtIsNull(porSku.keySet());
                List<Producto> modificados = new ArrayList<>();
                for (Producto local : locales) {
                    Variant v = porSku.remove(local.getSku().trim());
                    Product tn = productoPorSku.get(v.sku().trim());
                    Long productId = tn == null ? null : tn.id();
                    String handle = tn == null ? null : tn.handle();
                    boolean faltaLaFoto = sinImagen(local) && tn != null && tn.imagenUrl() != null;
                    if (!faltaLaFoto
                            && Objects.equals(local.getTiendanubeVariantId(), v.id())
                            && Objects.equals(local.getTiendanubeProductId(), productId)
                            && (handle == null || handle.equals(local.getTiendanubeHandle()))) {
                        yaMapeados++;
                        continue;
                    }
                    local.setTiendanubeProductId(productId);
                    local.setTiendanubeVariantId(v.id());
                    // Null no pisa: la tienda puede no devolverlo y el slug guardado sigue sirviendo.
                    if (handle != null) {
                        local.setTiendanubeHandle(handle);
                    }
                    // S-05: el maestro trae el link de imagen en 174 de 2252 filas, así que la foto
                    // sale de la tienda. Sólo rellena lo que está vacío: si el maestro puso una, gana
                    // la del maestro, que es la que el cliente eligió a mano.
                    if (sinImagen(local) && tn != null && tn.imagenUrl() != null) {
                        local.setImagenUrl(tn.imagenUrl());
                    }
                    modificados.add(local);
                }
                if (!modificados.isEmpty()) {
                    repo.saveAll(modificados);
                    mapeados += modificados.size();
                }
                sinMatch += porSku.size();
                for (String sku : porSku.keySet()) {
                    if (skusSinMatch.size() < MUESTRA_SIN_MATCH) {
                        skusSinMatch.add(sku);
                    }
                }
                if (!tnPage.hasNext()) {
                    break;
                }
            }
            health.registrarExito(Proveedor.TIENDANUBE);
        } catch (IntegrationUnavailableException ex) {
            health.registrarError(Proveedor.TIENDANUBE, ex.getMessage());
            throw ex;
        }
        // Recién ahora el catálogo tiene ids, así que se recalcula la publicación: lo que se acaba de
        // mapear pasa a ser recetable, y lo que quedó sin id de tienda deja de serlo. Sin esto habría
        // que correr además el sync de Contabilium para que la regla se aplique.
        int publicados = recalcularPublicacion();
        long pendientes = repo.countPublicadosSinMapear();
        log.info("[tiendanube-mapeo] revisados={} mapeados={} ya-mapeados={} sin-sku={} sin-match={} "
                        + "publicacion-recalculada={} pendientes={}",
                revisados, mapeados, yaMapeados, sinSku, sinMatch, publicados, pendientes);
        return new MapeoTiendaNubeResponse(
                revisados, mapeados, yaMapeados, sinSku, sinMatch, pendientes, List.copyOf(skusSinMatch), mapeadoAt);
    }

    /** Recalcula {@code publicado} en todo el catálogo. Devuelve cuántos productos cambiaron. */
    private int recalcularPublicacion() {
        boolean catalogoMapeado = repo.existsByTiendanubeProductIdIsNotNullAndDeletedAtIsNull();
        List<Producto> todos = repo.findByDeletedAtIsNull();
        List<Producto> cambiados = new ArrayList<>();
        for (Producto p : todos) {
            if (publicacionPolicy.aplicar(p, catalogoMapeado)) {
                cambiados.add(p);
            }
        }
        if (!cambiados.isEmpty()) {
            repo.saveAll(cambiados);
        }
        return cambiados.size();
    }

    private static boolean sinImagen(Producto p) {
        return p.getImagenUrl() == null || p.getImagenUrl().isBlank();
    }

    private static Map<String, Product> productoPorSku(List<Product> items) {
        Map<String, Product> porSku = new LinkedHashMap<>();
        for (Product p : items) {
            for (Variant v : p.variants()) {
                if (v.sku() != null && !v.sku().isBlank()) {
                    porSku.put(v.sku().trim(), p);
                }
            }
        }
        return porSku;
    }
}
