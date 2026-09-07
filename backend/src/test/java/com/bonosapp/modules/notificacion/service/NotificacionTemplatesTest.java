package com.bonosapp.modules.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Textos de los avisos del alta pública: son lo único que ve quien se registra. */
class NotificacionTemplatesTest {

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
        return new NotificacionTemplates(
                new NotificacionProperties(30000L, 5, 25, "admin@bonosapp.dev", appUrl),
                new IntegrationsProperties(null, tiendaNube(storeUrl), null));
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
                .contains("https://bonosapp.com.ar/nutricionistas");
    }

    /** Sin APP_PUBLIC_URL el mail sale igual: el link queda relativo, no roto ni con "null". */
    @Test
    void sinAppUrl_elLinkNoDiceNull() {
        assertThat(templates("").cuerpoRegistroAprobado(nutricionista))
                .contains("/ingresar")
                .doesNotContain("null");
    }
}
