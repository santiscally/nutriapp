package com.nutriapp.integrations.contabilium;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.nutriapp.integrations.IntegrationsProperties;
import com.nutriapp.integrations.contabilium.ContabiliumClient.ConceptoPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpContabiliumClientTest {

    private WireMockServer wm;

    @BeforeEach
    void up() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void down() {
        wm.stop();
    }

    private HttpContabiliumClient client() {
        return new HttpContabiliumClient(new IntegrationsProperties.Contabilium(
                "live", "http://localhost:" + wm.port(), "gon@jeianell.com.ar", "APIKEY"));
    }

    private void stubToken(String accessToken) {
        wm.stubFor(post(urlEqualTo("/token")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\",\"expires_in\":86399}")));
    }

    @Test
    void obtenerInfo_autenticaYParsea() {
        stubToken("T1");
        wm.stubFor(get(urlPathEqualTo("/api/usuarios/obtenerinfo")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"RazonSocial\":\"Jeianell SA\",\"Cuit\":\"20-12345678-3\"}")));

        var info = client().obtenerInfo();

        assertThat(info.razonSocial()).isEqualTo("Jeianell SA");
        assertThat(info.cuit()).isEqualTo("20-12345678-3");
        wm.verify(getRequestedFor(urlPathEqualTo("/api/usuarios/obtenerinfo"))
                .withHeader("Authorization", equalTo("Bearer T1")));
    }

    @Test
    void token_seCacheaEntreLlamadas() {
        stubToken("T1");
        wm.stubFor(get(urlPathEqualTo("/api/usuarios/obtenerinfo")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"RazonSocial\":\"X\",\"Cuit\":\"1\"}")));

        HttpContabiliumClient c = client();
        c.obtenerInfo();
        c.obtenerInfo();

        // Un solo password-grant para las dos llamadas.
        wm.verify(1, postRequestedFor(urlEqualTo("/token")));
    }

    @Test
    void buscarConceptos_parseaEnvelopePascalCase() {
        stubToken("T1");
        wm.stubFor(get(urlPathEqualTo("/api/conceptos/search")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "Items": [
                            {"Id":1,"Tipo":"Producto","Nombre":"OMEGA 3","Codigo":"SKU1",
                             "Descripcion":"cap","Estado":"Activo","Precio":100.0,"PrecioFinal":121.0,"Stock":5}
                          ],
                          "TotalPage": 2,
                          "TotalItems": 75
                        }
                        """)));

        ConceptoPage page = client().buscarConceptos("omega", 1);

        assertThat(page.totalPage()).isEqualTo(2);
        assertThat(page.totalItems()).isEqualTo(75);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).codigo()).isEqualTo("SKU1");
        assertThat(page.items().get(0).precioFinal()).isEqualByComparingTo("121.0");
        wm.verify(getRequestedFor(urlPathEqualTo("/api/conceptos/search"))
                .withQueryParam("filtro", equalTo("omega"))
                .withQueryParam("page", equalTo("1")));
    }

    @Test
    void ante401_renuevaTokenYReintentaUnaVez() {
        stubToken("T1");
        wm.stubFor(get(urlPathEqualTo("/api/usuarios/obtenerinfo"))
                .inScenario("expira")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("reintento"));
        wm.stubFor(get(urlPathEqualTo("/api/usuarios/obtenerinfo"))
                .inScenario("expira")
                .whenScenarioStateIs("reintento")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"RazonSocial\":\"OK\",\"Cuit\":\"9\"}")));

        var info = client().obtenerInfo();

        assertThat(info.razonSocial()).isEqualTo("OK");
        // 2 password-grants (el 401 forzó renovar) y 2 hits al endpoint.
        wm.verify(2, postRequestedFor(urlEqualTo("/token")));
        wm.verify(2, getRequestedFor(urlPathEqualTo("/api/usuarios/obtenerinfo")));
    }
}
