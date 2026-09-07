package com.bonosapp.modules.producto.maestro;

import static com.bonosapp.modules.producto.maestro.MaestroXlsxFixture.fila;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.bonosapp.common.error.UnprocessableException;
import com.bonosapp.modules.producto.CatalogoProperties;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.entity.ProductoTag;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.producto.service.PublicacionPolicy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaestroImportServiceTest {

    @Mock ProductoRepository productoRepository;
    @Mock MaestroImportacionRepository importacionRepository;

    MaestroImportService service;

    private static final CatalogoProperties PROPS =
            new CatalogoProperties(List.of("144331"), List.of("Producto"), new BigDecimal("100"), 20L * 1024 * 1024);

    @BeforeEach
    void setUp() {
        service = new MaestroImportService(new MaestroXlsxParser(), productoRepository,
                importacionRepository, new PublicacionPolicy(PROPS), PROPS);
    }

    /** Producto ya sincronizado desde Contabilium: recetable, sin nada del maestro todavía. */
    private Producto producto(String sku, Long contabiliumId) {
        Producto p = new Producto();
        p.setSku(sku);
        p.setContabiliumId(contabiliumId);
        p.setNombre("ON-ROLL FLOW X 60G");
        p.setPrecio(new BigDecimal("13500"));
        p.setStock(2309);
        p.setTipoErp("Producto");
        p.setRubroId("144331");
        p.setMarca("ON-ROLL");
        p.setPublicado(true);
        return p;
    }

    private MockMultipartFile archivo(String[]... filas) throws Exception {
        return new MockMultipartFile("archivo", "maestro.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                MaestroXlsxFixture.build(filas));
    }

    @Test
    void aplicaLosCamposDelMaestroYDejaIntactosLosDeContabilium() throws Exception {
        Producto p = producto("3", 9418829L);
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of(p));

        ImportarMaestroResponse r = service.importar(archivo(fila("3", "9418829", "ACTIVO", "magnesio\nsalud")));

        assertThat(r.filasLeidas()).isEqualTo(1);
        assertThat(r.filasMatcheadas()).isEqualTo(1);
        assertThat(r.filasActualizadas()).isEqualTo(1);
        assertThat(p.getDepartamento()).isEqualTo("SALUD Y BIENESTAR");
        assertThat(p.getCategoria()).isEqualTo("TERAPIAS NATURALES");
        assertThat(p.getSubcategoria()).isEqualTo("FLEBOTONICOS TOPICOS");
        assertThat(p.getLaboratorio()).isEqualTo("JEIANELL");
        assertThat(p.getTags()).extracting(ProductoTag::getTag).containsExactlyInAnyOrder("magnesio", "salud");
        assertThat(p.getMaestroSyncedAt()).isNotNull();
        // Lo del ERP no se toca ni por accidente.
        assertThat(p.getNombre()).isEqualTo("ON-ROLL FLOW X 60G");
        assertThat(p.getPrecio()).isEqualByComparingTo("13500");
        assertThat(p.getMarca()).isEqualTo("ON-ROLL");
        assertThat(p.getStock()).isEqualTo(2309);
    }

    @Test
    void estadoBloqueadoDespublicaElProducto() throws Exception {
        Producto p = producto("40", 12733947L);
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of(p));

        ImportarMaestroResponse r = service.importar(archivo(fila("40", "12733947", "BLOQUEADO", null)));

        assertThat(p.isBloqueadoMaestro()).isTrue();
        assertThat(p.isPublicado()).isFalse();
        assertThat(r.despublicados()).isEqualTo(1);
        assertThat(r.mensaje()).contains("dejaron de estar disponibles");
    }

    @Test
    void desbloquearVuelveAPublicar() throws Exception {
        Producto p = producto("40", 12733947L);
        p.setBloqueadoMaestro(true);
        p.setPublicado(false);
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of(p));

        ImportarMaestroResponse r = service.importar(archivo(fila("40", "12733947", "ACTIVO", null)));

        assertThat(p.isPublicado()).isTrue();
        assertThat(r.publicados()).isEqualTo(1);
    }

    @Test
    void skuSinProductoEnElCatalogo_seReportaNoSeInventa() throws Exception {
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of());

        ImportarMaestroResponse r = service.importar(archivo(fila("9999", "1", "ACTIVO", null)));

        assertThat(r.filasMatcheadas()).isZero();
        assertThat(r.filasSinMatch()).isEqualTo(1);
        assertThat(r.skusSinMatch()).containsExactly("9999");
        assertThat(r.mensaje()).contains("sin producto en el catálogo");
    }

    @Test
    void idContabiliumQueNoCoincide_rechazaLaFilaEnVezDePisarOtroProducto() throws Exception {
        // El mismo SKU apuntando a otro artículo: escribirlo sería mezclar dos productos.
        Producto p = producto("3", 9418829L);
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of(p));

        ImportarMaestroResponse r = service.importar(archivo(fila("3", "77777777", "ACTIVO", null)));

        assertThat(r.filasRechazadas()).isEqualTo(1);
        assertThat(r.filasMatcheadas()).isZero();
        assertThat(r.rechazos().get(0)).contains("77777777").contains("9418829");
        assertThat(p.getDepartamento()).isNull();
    }

    @Test
    void skuRepetido_seQuedaConElPrimeroYReportaElResto() throws Exception {
        Producto p = producto("3", 9418829L);
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of(p));

        ImportarMaestroResponse r = service.importar(archivo(
                fila("3", "9418829", "ACTIVO", "primero"),
                fila("3", "9418829", "BLOQUEADO", "segundo")));

        assertThat(r.filasMatcheadas()).isEqualTo(1);
        assertThat(r.filasRechazadas()).isEqualTo(1);
        assertThat(p.isBloqueadoMaestro()).isFalse();
        assertThat(p.getTags()).extracting(ProductoTag::getTag).containsExactly("primero");
    }

    @Test
    void reimportarLoMismo_noCuentaCambios() throws Exception {
        Producto p = producto("3", 9418829L);
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of(p));

        service.importar(archivo(fila("3", "9418829", "ACTIVO", "magnesio")));
        ImportarMaestroResponse segunda = service.importar(archivo(fila("3", "9418829", "ACTIVO", "magnesio")));

        assertThat(segunda.filasMatcheadas()).isEqualTo(1);
        assertThat(segunda.filasActualizadas()).isZero();
    }

    @Test
    void tagsBorradosEnLaPlanilla_desaparecenDelProducto() throws Exception {
        Producto p = producto("3", 9418829L);
        p.getTags().add(ProductoTag.of("viejo"));
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of(p));

        service.importar(archivo(fila("3", "9418829", "ACTIVO", "nuevo")));

        assertThat(p.getTags()).extracting(ProductoTag::getTag).containsExactly("nuevo");
    }

    @Test
    void archivoQueNoEsXlsx_seRechazaPorExtension() {
        MockMultipartFile csv = new MockMultipartFile("archivo", "maestro.csv", "text/csv", "a,b".getBytes());

        assertThatThrownBy(() -> service.importar(csv))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void archivoVacio_seRechaza() {
        MockMultipartFile vacio = new MockMultipartFile("archivo", "maestro.xlsx", null, new byte[0]);

        assertThatThrownBy(() -> service.importar(vacio)).isInstanceOf(UnprocessableException.class);
    }

    @Test
    void registraLaImportacionParaElPanelDeAdmin() throws Exception {
        when(productoRepository.findParaImportacion(anyCollection())).thenReturn(List.of());

        service.importar(archivo(fila("1", "1", "ACTIVO", null)));

        org.mockito.ArgumentCaptor<MaestroImportacion> captor =
                org.mockito.ArgumentCaptor.forClass(MaestroImportacion.class);
        org.mockito.Mockito.verify(importacionRepository).save(captor.capture());
        assertThat(captor.getValue().getNombreArchivo()).isEqualTo("maestro.xlsx");
        assertThat(captor.getValue().getFilasLeidas()).isEqualTo(1);
        assertThat(captor.getValue().getDetalle()).contains("1");
    }
}
