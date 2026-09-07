package com.bonosapp.modules.receta.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.modules.paciente.entity.Paciente;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WaMeLinkBuilderTest {

    private static final String TIENDA = "https://tienda.test";

    private final WaMeLinkBuilder builder = builder(TIENDA);

    private static WaMeLinkBuilder builder(String storeUrl) {
        return new WaMeLinkBuilder(new IntegrationsProperties(
                null,
                new IntegrationsProperties.TiendaNube(
                        "stub", null, null, null, null, null, null, null, storeUrl),
                null));
    }

    private Receta receta(EstadoReceta estado) {
        Receta r = new Receta();
        r.setCodigo("RX-3V737V");
        r.setEstado(estado);
        r.setDescuentoPct(new BigDecimal("15.00"));
        r.setVenceAt(LocalDate.of(2026, 9, 3));
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

    @Test
    void elMensajeLlevaElLinkDeLaTienda() {
        String texto = texto(builder.forReceta(receta(EstadoReceta.PENDIENTE), paciente("+5491144443333")));

        assertThat(texto).endsWith("Usalo al comprar acá: " + TIENDA);
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

        assertThat(texto).endsWith("acá: https://tienda.test");
    }

    private static String texto(String url) {
        return URLDecoder.decode(
                url.substring(url.indexOf("?text=") + "?text=".length()), StandardCharsets.UTF_8);
    }
}
