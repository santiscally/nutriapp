package com.bonosapp.modules.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.paciente.entity.Paciente;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.entity.RecetaItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Textos de los avisos del alta pública (lo único que ve quien se registra) y del mail del bono
 * que recibe la paciente.
 */
class NotificacionTemplatesTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);

    private Nutricionista nutricionista;

    @BeforeEach
    void setup() {
        nutricionista = new Nutricionista();
        nutricionista.setNombre("Ana");
        nutricionista.setApellido("Gómez");
        nutricionista.setEmail("ana@example.com");
        nutricionista.setMatricula("MN 12345");
        nutricionista.setDni("30111222");
    }

    private NotificacionTemplates templates(String appUrl) {
        return templates(appUrl, null);
    }

    private NotificacionTemplates templates(String appUrl, String storeUrl) {
        IntegrationsProperties integrations = new IntegrationsProperties(null, tiendaNube(storeUrl), null);
        return new NotificacionTemplates(
                new NotificacionProperties(30000L, 5, 25, "admin@bonosapp.dev", appUrl),
                integrations,
                new BonoContenido(productos, integrations));
    }

    private Receta receta() {
        Receta r = new Receta();
        r.setCodigo("RX-3V737V");
        r.setDescuentoPct(new BigDecimal("15.00"));
        r.setVenceAt(LocalDate.of(2026, 9, 3));
        return r;
    }

    /** Receta con un item que resuelve a un producto con nombre (F-19). */
    private Receta recetaConProducto(String nombre) {
        Receta r = receta();
        RecetaItem item = new RecetaItem();
        item.setProductoId(UUID.randomUUID());
        r.addItem(item);

        Producto p = new Producto();
        p.setNombre(nombre);
        p.setTiendanubeHandle("magnesio-300g");
        when(productos.findAllById(any())).thenReturn(List.of(p));
        return r;
    }

    private static Paciente paciente() {
        Paciente p = new Paciente();
        p.setNombre("Juan");
        return p;
    }

    private static IntegrationsProperties.TiendaNube tiendaNube(String storeUrl) {
        return new IntegrationsProperties.TiendaNube(
                "stub", null, null, null, null, null, null, null, storeUrl);
    }

    @Test
    void registroRecibido_nombraElEmailConElQueVaAEntrar() {
        String cuerpo = templates("https://bonosapp.com.ar").cuerpoRegistroRecibido(nutricionista);

        assertThat(cuerpo).contains("Ana").contains("ana@example.com");
    }

    @Test
    void registroAprobado_linkeaAlLogin_sinBarraDuplicada() {
        String cuerpo = templates("https://bonosapp.com.ar/").cuerpoRegistroAprobado(nutricionista);

        assertThat(cuerpo).contains("https://bonosapp.com.ar/ingresar");
        assertThat(cuerpo).doesNotContain("//ingresar");
    }

    @Test
    void registroRechazado_incluyeElMotivoSoloSiLoHay() {
        NotificacionTemplates templates = templates("https://bonosapp.com.ar");

        assertThat(templates.cuerpoRegistroRechazado(nutricionista, "La matrícula no es legible"))
                .contains("Motivo: La matrícula no es legible");
        assertThat(templates.cuerpoRegistroRechazado(nutricionista, "  ")).doesNotContain("Motivo:");
    }

    @Test
    void avisoAlAdmin_traeLosDatosParaValidar_yElLinkALaBandeja() {
        NotificacionTemplates templates = templates("https://bonosapp.com.ar");

        assertThat(templates.asuntoAdminNuevaSolicitud(nutricionista)).contains("Ana Gómez");
        assertThat(templates.cuerpoAdminNuevaSolicitud(nutricionista))
                .contains("ana@example.com")
                .contains("MN 12345")
                .contains("30111222")
                .contains("https://bonosapp.com.ar/profesionales");
    }

    // --- Mail del bono al paciente (F-18 / F-19 / F-22) ---

    /** F-19: el mail dice de qué producto es el bono; F-18: linkea el cupón, que se aplica solo. */
    @Test
    void mailDelBono_nombraElProducto_yLinkeaElCupon() {
        String cuerpo = templates("https://bonosapp.com.ar", "https://tienda.test")
                .cuerpoEmail(recetaConProducto("Magnesio 300g"), paciente());

        assertThat(cuerpo)
                .contains("Tu bono profesional de Magnesio 300g con 15% de descuento")
                .contains("Código: RX-3V737V")
                .contains("03/09/2026")
                .contains("No combinable con promociones activas")
                .contains("https://tienda.test/discount/RX-3V737V");
    }

    /** Sin tienda configurada el mail sale igual: vuelve a "tipeá el código", sin link roto. */
    @Test
    void mailDelBono_sinTienda_noDejaUnLinkCortado() {
        String cuerpo = templates("https://bonosapp.com.ar").cuerpoEmail(receta(), paciente());

        assertThat(cuerpo)
                .contains("Usá el código al finalizar tu compra")
                .doesNotContain("/discount/")
                .doesNotContain("null");
    }

    /** F-18: primero el link que aplica el bono, después la ficha del producto. */
    @Test
    void mailDelBono_linkeaElCuponYDespuesElProducto() {
        String cuerpo = templates("https://bonosapp.com.ar", "https://tienda.test")
                .cuerpoEmail(recetaConProducto("Magnesio 300g"), paciente());

        assertThat(cuerpo.indexOf("https://tienda.test/discount/RX-3V737V"))
                .isLessThan(cuerpo.indexOf("https://tienda.test/productos/magnesio-300g"));
    }

    /** F-22: el asunto identifica el mail sin palabras de promoción. */
    @Test
    void asuntoDelBono_esTransaccional_noPromocional() {
        assertThat(templates("https://bonosapp.com.ar").asuntoEmail(receta()))
                .isEqualTo("Tu bono profesional RX-3V737V");
    }

    /** Sin APP_PUBLIC_URL el mail sale igual: el link queda relativo, no roto ni con "null". */
    @Test
    void sinAppUrl_elLinkNoDiceNull() {
        assertThat(templates("").cuerpoRegistroAprobado(nutricionista))
                .contains("/ingresar")
                .doesNotContain("null");
    }
}
