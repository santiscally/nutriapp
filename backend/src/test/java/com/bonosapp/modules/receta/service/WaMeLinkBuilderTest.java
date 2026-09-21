package com.bonosapp.modules.receta.service;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.modules.notificacion.service.BonoContenido;
import com.bonosapp.modules.paciente.entity.Paciente;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.entity.RecetaItem;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WaMeLinkBuilderTest {

    private static final String TIENDA = "https://tienda.test";

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final WaMeLinkBuilder builder = builder(TIENDA);

    private WaMeLinkBuilder builder(String storeUrl) {
        IntegrationsProperties props = new IntegrationsProperties(
                null,
                new IntegrationsProperties.TiendaNube(
                        "stub", null, null, null, null, null, null, null, storeUrl),
                null);
        return new WaMeLinkBuilder(props, new BonoContenido(productos, props));
    }

    private Receta receta(EstadoReceta estado) {
        Receta r = new Receta();
        r.setCodigo("RX-3V737V");
        r.setEstado(estado);
        r.setDescuentoPct(new BigDecimal("15.00"));
        r.setVenceAt(LocalDate.of(2026, 9, 3));
        return r;
    }

    /** Receta con un item resuelto a un producto con nombre (F-19). */
    private Receta recetaConProducto(String nombre) {
        Receta r = receta(EstadoReceta.PENDIENTE);
        RecetaItem item = new RecetaItem();
        item.setProductoId(UUID.randomUUID());
        r.addItem(item);

        Producto p = new Producto();
        p.setNombre(nombre);
        when(productos.findAllById(any())).thenReturn(List.of(p));
        return r;
    }

    private Paciente paciente(String whatsapp) {
        Paciente p = new Paciente();
        p.setNombre("Juan");
        p.setWhatsapp(whatsapp);
        return p;
    }

    @Test
    void arma_elLinkConElTelefonoSinMasYElTextoEncodeado() {
        String url = builder.forReceta(receta(EstadoReceta.PENDIENTE), paciente("+5491144443333"));

        assertThat(url).startsWith("https://wa.me/5491144443333?text=");
        String texto = URLDecoder.decode(url.substring(url.indexOf("?text=") + 6), StandardCharsets.UTF_8);
        assertThat(texto)
                .contains("Juan")
                .contains("*RX-3V737V*")
                .contains("15%")
                .contains("03/09/2026");
    }

    /**
     * URLEncoder es form-encoding y manda los espacios como '+', que algunos clientes de WhatsApp
     * muestran literal en el mensaje. Tienen que ir como %20.
     */
    @Test
    void codificaLosEspaciosComoPorciento20_noComoMas() {
        String url = builder.forReceta(receta(EstadoReceta.PENDIENTE), paciente("+5491144443333"));

        assertThat(url).doesNotContain("+");
        assertThat(url).contains("%20");
    }

    @Test
    void normalizaTelefonosConSeparadores() {
        String url = builder.forReceta(receta(EstadoReceta.PENDIENTE), paciente("+54 9 11 4444-3333"));

        assertThat(url).startsWith("https://wa.me/5491144443333?text=");
    }

    /** Sólo una receta PENDIENTE tiene un cupón que valga la pena mandar. */
    @ParameterizedTest
    @EnumSource(value = EstadoReceta.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
    void noOfreceLinkSiLaRecetaYaNoSePuedeUsar(EstadoReceta estado) {
        assertThat(builder.forReceta(receta(estado), paciente("+5491144443333"))).isNull();
    }

    @Test
    void noOfreceLinkSinTelefonoUtilizable() {
        Receta pendiente = receta(EstadoReceta.PENDIENTE);

        assertThat(builder.forReceta(pendiente, paciente(null))).isNull();
        assertThat(builder.forReceta(pendiente, paciente("   "))).isNull();
        assertThat(builder.forReceta(pendiente, paciente("sin-numero"))).isNull();
        assertThat(builder.forReceta(pendiente, null)).isNull();
    }

    /** Un emoji que no esté en la fuente del cliente de la paciente se ve como caja vacía. */
    @Test
    void elMensajeNoLlevaEmojis() {
        String url = builder.forReceta(receta(EstadoReceta.PENDIENTE), paciente("+5491144443333"));
        String texto = URLDecoder.decode(
                url.substring(url.indexOf("?text=") + "?text=".length()), StandardCharsets.UTF_8);

        assertThat(texto).startsWith("Hola Juan! Tu bono profesional");
        assertThat(texto.codePoints().anyMatch(cp -> cp >= 0x2600)).isFalse();
    }

    /** F-18: el link que se comparte es el de cupón, el que aplica el bono solo al abrirlo. */
    @Test
    void elMensajeLlevaElLinkDeCuponYLaInstruccion() {
        String texto = texto(builder.forReceta(receta(EstadoReceta.PENDIENTE), paciente("+5491144443333")));

        assertThat(texto)
                .contains("sumá el producto al carrito")
                .contains("No combinable con promociones activas")
                .endsWith(TIENDA + "/discount/RX-3V737V");
    }

    /** F-19: el mensaje dice de qué producto es el bono. */
    @Test
    void elMensajeNombraElProductoDelBono() {
        String texto = texto(builder.forReceta(recetaConProducto("Magnesio 300g"), paciente("+5491144443333")));

        assertThat(texto).contains("Tu bono profesional de Magnesio 300g con 15% de descuento");
    }

    /** Sin tienda configurada el mensaje sale igual, sin un link cortado. */
    @Test
    void sinTiendaConfiguradaNoQuedaUnLinkVacio() {
        String texto = texto(builder(null).forReceta(receta(EstadoReceta.PENDIENTE), paciente("+5491144443333")));

        assertThat(texto).endsWith("Usalo al comprar en la tienda online.").doesNotContain("http");
    }

    /** La barra final del env no se duplica contra la del path. */
    @Test
    void laBarraFinalDeLaUrlSeNormaliza() {
        String texto = texto(builder("https://tienda.test/").forReceta(
                receta(EstadoReceta.PENDIENTE), paciente("+5491144443333")));

        assertThat(texto).endsWith("https://tienda.test/discount/RX-3V737V");
    }

    private static String texto(String url) {
        return URLDecoder.decode(
                url.substring(url.indexOf("?text=") + "?text=".length()), StandardCharsets.UTF_8);
    }
}
