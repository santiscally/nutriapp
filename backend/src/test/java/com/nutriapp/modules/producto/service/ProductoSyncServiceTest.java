package com.nutriapp.modules.producto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.contabilium.ContabiliumClient;
import com.nutriapp.integrations.contabilium.ContabiliumClient.Concepto;
import com.nutriapp.integrations.contabilium.ContabiliumClient.ConceptoPage;
import com.nutriapp.integrations.health.IntegrationHealthRegistry;
import com.nutriapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.nutriapp.modules.producto.CatalogoProperties;
import com.nutriapp.modules.producto.dto.SyncProductosResponse;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.entity.ProductoTag;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoSyncServiceTest {

    @Mock ContabiliumClient contabiliumClient;
    @Mock ProductoRepository productoRepository;
    @Mock IntegrationHealthRegistry health;

    ProductoSyncService service;

    /** Sin filtro de rubro ni de tipo: solo aplican precio, estado del ERP y bloqueo del maestro. */
    private static final CatalogoProperties SIN_FILTROS =
            new CatalogoProperties(List.of(), List.of(), new BigDecimal("100"), 0);

    /** Como en producción: solo "producto terminado" (144331) y solo Tipo=Producto. */
    private static final CatalogoProperties COMO_PROD =
            new CatalogoProperties(List.of("144331"), List.of("Producto"), new BigDecimal("100"), 0);

    @BeforeEach
    void setUp() {
        configurar(SIN_FILTROS);
        // sync() pide los rubros una vez al inicio; sin data de rubro los conceptos quedan sin rubro/marca.
        when(contabiliumClient.rubrosLookup()).thenReturn(ContabiliumClient.RubrosLookup.vacio());
    }

    private void configurar(CatalogoProperties props) {
        service = new ProductoSyncService(contabiliumClient, productoRepository, health,
                new PublicacionPolicy(props));
    }

    private Concepto concepto(Long id, String sku, String nombre, BigDecimal precioFinal, Integer stock) {
        return concepto(id, sku, nombre, precioFinal, stock, "activo");
    }

    private Concepto concepto(Long id, String sku, String nombre, BigDecimal precioFinal, Integer stock,
                              String estado) {
        return new Concepto(id, "P", nombre, sku, null, "desc " + sku, estado, null, precioFinal,
                stock, null, null);
    }

    /** Variante con los campos que gobiernan la publicación: Tipo, IdRubro y código de barras. */
    private Concepto conceptoErp(Long id, String sku, String tipo, String idRubro, String codigoBarras) {
        return new Concepto(id, tipo, "Prod " + sku, sku, codigoBarras, "desc", "Activo", null,
                new BigDecimal("5000"), 10, idRubro, null);
    }

    /** Corre la sync con un solo concepto nuevo y devuelve el Producto que se guardó. */
    private Producto sincronizarUno(Concepto c) {
        when(contabiliumClient.buscarConceptos("", 1)).thenReturn(new ConceptoPage(List.of(c), 1, 1));
        when(productoRepository.findBySkuAndDeletedAtIsNull(c.codigo())).thenReturn(Optional.empty());
        service.sync();
        org.mockito.ArgumentCaptor<Producto> captor = org.mockito.ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- C-14 (post-demo 2026-07-31): el Estado de Contabilium decide si es recetable ---

    @Test
    void sync_conceptoInactivoEnContabilium_noSePublica() {
        Producto guardado = sincronizarUno(
                concepto(10L, "SKU-INACT", "Discontinuado", new BigDecimal("5000"), 12, "Inactivo"));

        // Precio válido, pero el ERP lo tiene dado de baja → no se puede recetar.
        assertThat(guardado.isPublicado()).isFalse();
    }

    @Test
    void sync_conceptoActivoConPrecioValido_sePublica() {
        Producto guardado = sincronizarUno(
                concepto(11L, "SKU-OK", "Magnesio", new BigDecimal("5000"), 12, "Activo"));

        assertThat(guardado.isPublicado()).isTrue();
    }

    @Test
    void sync_estadoDesconocidoONulo_asumeActivo() {
        // Defensivo: si Contabilium cambia el vocabulario preferimos publicar de más antes que
        // vaciar el catálogo entero de un sync para el otro.
        assertThat(sincronizarUno(
                concepto(12L, "SKU-NULL", "Sin estado", new BigDecimal("5000"), 1, null)).isPublicado())
                .isTrue();
    }

    // --- Mail de Gon 2026-08-03: código de barras, Tipo=Producto y rubro "producto terminado" ---

    @Test
    void sync_persisteElCodigoDeBarras() {
        Producto guardado = sincronizarUno(conceptoErp(20L, "208", "Producto", "144331", "7790839000267"));

        assertThat(guardado.getCodigoBarras()).isEqualTo("7790839000267");
    }

    @Test
    void sync_tipoServicio_noSePublica() {
        configurar(COMO_PROD);

        Producto guardado = sincronizarUno(conceptoErp(21L, "SRV-1", "Servicio", "144331", null));

        assertThat(guardado.getTipoErp()).isEqualTo("Servicio");
        assertThat(guardado.isPublicado()).isFalse();
    }

    @Test
    void sync_combo_conSoloProductoPermitido_noSePublica() {
        // El Tipo de Contabilium tiene TRES valores en la cuenta de TBC: Producto (2004),
        // Combo (209) y Servicio (54). "Solo Producto", tal como lo pidió Gon, saca los packs.
        configurar(COMO_PROD);

        assertThat(sincronizarUno(conceptoErp(25L, "COMBO-1", "Combo", "144331", null)).isPublicado())
                .isFalse();
    }

    @Test
    void sync_combo_habilitarloEsSoloConfig() {
        configurar(new CatalogoProperties(List.of("144331"), List.of("Producto", "Combo"),
                new BigDecimal("100"), 0));

        assertThat(sincronizarUno(conceptoErp(26L, "COMBO-2", "Combo", "144331", null)).isPublicado())
                .isTrue();
    }

    @Test
    void sync_rubroFueraDeLosPermitidos_noSePublica() {
        configurar(COMO_PROD);

        // Insumos / materias primas / gastos: existen en el ERP pero no se recetan (C-13).
        Producto guardado = sincronizarUno(conceptoErp(22L, "INS-1", "Producto", "999999", null));

        assertThat(guardado.getRubroId()).isEqualTo("999999");
        assertThat(guardado.isPublicado()).isFalse();
    }

    @Test
    void sync_productoTerminadoActivo_sePublica() {
        configurar(COMO_PROD);

        assertThat(sincronizarUno(conceptoErp(23L, "PT-1", "Producto", "144331", null)).isPublicado()).isTrue();
    }

    @Test
    void sync_conceptoSinRubroNiTipo_sePublicaIgual() {
        configurar(COMO_PROD);

        // Ante datos faltantes se publica: un ERP que deja de mandar el campo no puede vaciar el catálogo.
        assertThat(sincronizarUno(conceptoErp(24L, "S/D", null, null, null)).isPublicado()).isTrue();
    }

    // --- Convivencia con el maestro (07-...md §3.2) ---

    @Test
    void sync_noPisaLosCamposDelMaestro() {
        Concepto c = conceptoErp(30L, "SKU-M", "Producto", "144331", null);
        when(contabiliumClient.buscarConceptos("", 1)).thenReturn(new ConceptoPage(List.of(c), 1, 1));

        Producto existente = new Producto();
        existente.setSku("SKU-M");
        existente.setDepartamento("SALUD Y BIENESTAR");
        existente.setCategoria("SUPLEMENTOS DIETARIOS");
        existente.setSubcategoria("MULTIVITAMINICOS");
        existente.setLaboratorio("FRAMINGHAM");
        existente.setDescripcionWeb("texto largo del maestro");
        existente.getTags().add(ProductoTag.of("magnesio"));
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-M")).thenReturn(Optional.of(existente));

        service.sync();

        assertThat(existente.getDepartamento()).isEqualTo("SALUD Y BIENESTAR");
        assertThat(existente.getCategoria()).isEqualTo("SUPLEMENTOS DIETARIOS");
        assertThat(existente.getSubcategoria()).isEqualTo("MULTIVITAMINICOS");
        assertThat(existente.getLaboratorio()).isEqualTo("FRAMINGHAM");
        assertThat(existente.getDescripcionWeb()).isEqualTo("texto largo del maestro");
        assertThat(existente.getTags()).hasSize(1);
    }

    @Test
    void sync_bloqueadoEnElMaestro_noSePublicaAunqueElErpLoTengaActivo() {
        Concepto c = conceptoErp(31L, "SKU-B", "Producto", "144331", null);
        when(contabiliumClient.buscarConceptos("", 1)).thenReturn(new ConceptoPage(List.of(c), 1, 1));

        Producto existente = new Producto();
        existente.setSku("SKU-B");
        existente.setBloqueadoMaestro(true);
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-B")).thenReturn(Optional.of(existente));

        service.sync();

        assertThat(existente.isActivoErp()).isTrue();
        assertThat(existente.isPublicado()).isFalse();
    }

    // --- Comportamiento general de la sync ---

    @Test
    void sync_stub_propaga503YRegistraError() {
        when(contabiliumClient.buscarConceptos(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IntegrationUnavailableException("contabilium"));

        assertThatThrownBy(() -> service.sync())
                .isInstanceOf(IntegrationUnavailableException.class);

        verify(health).registrarError(eq(Proveedor.CONTABILIUM), any());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void sync_live_creaNuevoYActualizaExistente() {
        Concepto nuevo = concepto(1L, "SKU-A", "Prod A", new BigDecimal("100"), 5);
        Concepto existenteCambiado = concepto(2L, "SKU-B", "Prod B nuevo", new BigDecimal("20"), 3);
        when(contabiliumClient.buscarConceptos("", 1))
                .thenReturn(new ConceptoPage(List.of(nuevo, existenteCambiado), 1, 2));

        Producto b = new Producto();
        b.setSku("SKU-B");
        b.setNombre("Prod B viejo");
        b.setPrecio(new BigDecimal("10"));
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-A")).thenReturn(Optional.empty());
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-B")).thenReturn(Optional.of(b));

        SyncProductosResponse resp = service.sync();

        assertThat(resp.revisados()).isEqualTo(2);
        assertThat(resp.creados()).isEqualTo(1);
        assertThat(resp.actualizados()).isEqualTo(1);
        assertThat(resp.sinCambios()).isZero();
        assertThat(resp.syncedAt()).isNotNull();
        verify(productoRepository, org.mockito.Mockito.times(2)).save(any(Producto.class));
        verify(health).registrarExito(Proveedor.CONTABILIUM);
    }

    @Test
    void sync_live_existenteSinCambios() {
        // Precio por encima del umbral: con menos de $100 el producto se despublica (regla de
        // "precio irrisorio = producto de baja") y eso ya sería un cambio.
        Concepto igual = concepto(3L, "SKU-C", "Prod C", new BigDecimal("500"), 7);
        when(contabiliumClient.buscarConceptos("", 1))
                .thenReturn(new ConceptoPage(List.of(igual), 1, 1));

        Producto c = new Producto();
        c.setSku("SKU-C");
        c.setNombre("Prod C");
        c.setDescripcion("desc SKU-C");
        c.setPrecio(new BigDecimal("500"));
        c.setStock(7);
        c.setTipoErp("P");
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-C")).thenReturn(Optional.of(c));

        SyncProductosResponse resp = service.sync();

        assertThat(resp.revisados()).isEqualTo(1);
        assertThat(resp.creados()).isZero();
        assertThat(resp.actualizados()).isZero();
        assertThat(resp.sinCambios()).isEqualTo(1);
    }

    @Test
    void sync_conceptoSinSku_seSaltea() {
        Concepto sinSku = concepto(4L, null, "Sin código", new BigDecimal("10"), 1);
        when(contabiliumClient.buscarConceptos("", 1))
                .thenReturn(new ConceptoPage(List.of(sinSku), 1, 1));

        SyncProductosResponse resp = service.sync();

        assertThat(resp.revisados()).isEqualTo(1);
        assertThat(resp.creados()).isZero();
        assertThat(resp.actualizados()).isZero();
        assertThat(resp.sinCambios()).isZero();
        verify(productoRepository, never()).findBySkuAndDeletedAtIsNull(any());
        verify(productoRepository, never()).save(any());
    }
}
