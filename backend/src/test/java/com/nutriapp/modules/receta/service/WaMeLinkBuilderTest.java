package com.nutriapp.modules.receta.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WaMeLinkBuilderTest {

    private final WaMeLinkBuilder builder = new WaMeLinkBuilder();

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
}
