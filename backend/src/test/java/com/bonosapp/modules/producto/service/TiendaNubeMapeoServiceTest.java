package com.bonosapp.modules.producto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TiendaNubeMapeoServiceTest {

    @Mock ProductoRepository repo;
    @Mock TiendaNubeClient tiendaNubeClient;
    @Mock IntegrationHealthRegistry health;
    @Mock PublicacionPolicy publicacionPolicy;

    private TiendaNubeMapeoService service;

    @BeforeEach
    void setUp() {
        service = new TiendaNubeMapeoService(repo, tiendaNubeClient, health, publicacionPolicy);
    }

    private static Producto local(String sku) {
        Producto p = new Producto();
        p.setSku(sku);
        p.setNombre("local " + sku);
        return p;
    }

    private static ProductPage page(boolean hasNext, Product... items) {
        return new ProductPage(List.of(items), hasNext);
    }

    @Test
    void escribeLosIdsDeTiendaNubeMatcheandoPorSku() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt())).thenReturn(
                page(false, new Product(363154002L, "ON-ROLL", "slug-363154002", List.of(new Variant(1583970721L, "119")))));
        Producto p = local("119");
        when(repo.findBySkuInAndDeletedAtIsNull(any())).thenReturn(List.of(p));

        MapeoTiendaNubeResponse r = service.mapear();

        assertThat(p.getTiendanubeProductId()).isEqualTo(363154002L);
        assertThat(p.getTiendanubeVariantId()).isEqualTo(1583970721L);
        assertThat(r.mapeados()).isEqualTo(1);
        assertThat(r.revisados()).isEqualTo(1);
        verify(repo).saveAll(List.of(p));
        verify(health).registrarExito(Proveedor.TIENDANUBE);
    }

    @Test
    void productIdYVariantIdSonDistintosYNoSeConfunden() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt())).thenReturn(
                page(false, new Product(100L, "x", "slug-100", List.of(new Variant(999L, "119")))));
        Producto p = local("119");
        when(repo.findBySkuInAndDeletedAtIsNull(any())).thenReturn(List.of(p));

        service.mapear();

        assertThat(p.getTiendanubeProductId()).isEqualTo(100L);
        assertThat(p.getTiendanubeVariantId()).isEqualTo(999L);
    }

    /** S-02: un catálogo ya mapeado antes del handle lo completa en la corrida siguiente. */
    @Test
    void alProductoYaMapeadoSinHandleSeLoCompleta() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt())).thenReturn(
                page(false, new Product(100L, "x", "on-roll-flow", List.of(new Variant(999L, "119")))));
        Producto p = local("119");
        p.setTiendanubeProductId(100L);
        p.setTiendanubeVariantId(999L);
        when(repo.findBySkuInAndDeletedAtIsNull(any())).thenReturn(List.of(p));

        MapeoTiendaNubeResponse r = service.mapear();

        assertThat(p.getTiendanubeHandle()).isEqualTo("on-roll-flow");
        assertThat(r.mapeados()).isEqualTo(1);
    }

    @Test
    void loQueYaEstabaMapeadoNoSeReescribe() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt())).thenReturn(
                page(false, new Product(100L, "x", "slug-100", List.of(new Variant(999L, "119")))));
        Producto p = local("119");
        p.setTiendanubeProductId(100L);
        p.setTiendanubeVariantId(999L);
        p.setTiendanubeHandle("slug-100");
        when(repo.findBySkuInAndDeletedAtIsNull(any())).thenReturn(List.of(p));

        MapeoTiendaNubeResponse r = service.mapear();

        assertThat(r.yaMapeados()).isEqualTo(1);
        assertThat(r.mapeados()).isZero();
        verify(repo, never()).saveAll(any());
    }

    @Test
    void reportaLosSkusDeLaTiendaQueNoEstanEnElCatalogo() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt())).thenReturn(
                page(false, new Product(1L, "fantasma", "slug-1", List.of(new Variant(2L, "NO-EXISTE")))));
        when(repo.findBySkuInAndDeletedAtIsNull(any())).thenReturn(List.of());

        MapeoTiendaNubeResponse r = service.mapear();

        assertThat(r.sinMatch()).isEqualTo(1);
        assertThat(r.skusSinMatch()).containsExactly("NO-EXISTE");
        assertThat(r.mapeados()).isZero();
    }

    @Test
    void laVarianteSinSkuSeSaltea() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt())).thenReturn(
                page(false, new Product(1L, "sin sku", "slug-1", List.of(new Variant(2L, null)))));

        MapeoTiendaNubeResponse r = service.mapear();

        assertThat(r.sinSku()).isEqualTo(1);
        assertThat(r.sinMatch()).isZero();
        verify(repo, never()).findBySkuInAndDeletedAtIsNull(any());
    }

    @Test
    void recorreTodasLasPaginasHastaQueNoHayaSiguiente() {
        when(tiendaNubeClient.listProducts(1, 200)).thenReturn(
                page(true, new Product(1L, "a", "slug-1", List.of(new Variant(11L, "A")))));
        when(tiendaNubeClient.listProducts(2, 200)).thenReturn(
                page(false, new Product(2L, "b", "slug-2", List.of(new Variant(22L, "B")))));
        when(repo.findBySkuInAndDeletedAtIsNull(any())).thenAnswer(inv -> {
            Collection<String> skus = inv.getArgument(0);
            return skus.stream().map(TiendaNubeMapeoServiceTest::local).toList();
        });

        MapeoTiendaNubeResponse r = service.mapear();

        assertThat(r.revisados()).isEqualTo(2);
        assertThat(r.mapeados()).isEqualTo(2);
        verify(tiendaNubeClient).listProducts(1, 200);
        verify(tiendaNubeClient).listProducts(2, 200);
        verify(tiendaNubeClient, never()).listProducts(3, 200);
    }

    @Test
    void enStubPropagaElErrorYLoRegistraEnHealth() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt()))
                .thenThrow(new IntegrationUnavailableException("tiendanube"));

        assertThatThrownBy(() -> service.mapear())
                .isInstanceOf(IntegrationUnavailableException.class);

        verify(health).registrarError(any(), any());
        verify(repo, never()).saveAll(any());
    }

    @Test
    void cuentaLosPublicadosQueSiguenSinMapear() {
        when(tiendaNubeClient.listProducts(anyInt(), anyInt())).thenReturn(page(false));
        when(repo.countPublicadosSinMapear()).thenReturn(2263L);

        assertThat(service.mapear().pendientes()).isEqualTo(2263L);
    }
}
