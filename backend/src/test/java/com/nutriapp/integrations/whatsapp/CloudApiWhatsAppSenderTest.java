package com.nutriapp.integrations.whatsapp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.IntegrationsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

class CloudApiWhatsAppSenderTest {

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

    private CloudApiWhatsAppSender sender() {
        return new CloudApiWhatsAppSender(new IntegrationsProperties.WhatsApp(
                "live", "http://localhost:" + wm.port(), "PHONE1", "WATOKEN"));
    }

    @Test
    void mandaTextoConBearerYNumeroSinMas() {
        wm.stubFor(post(urlEqualTo("/PHONE1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.X\"}]}")));

        sender().send("+5491122223333", "Hola! Tu receta RX-1");

        wm.verify(postRequestedFor(urlEqualTo("/PHONE1/messages"))
                .withHeader("Authorization", equalTo("Bearer WATOKEN"))
                .withRequestBody(matchingJsonPath("$.messaging_product", equalTo("whatsapp")))
                .withRequestBody(matchingJsonPath("$.type", equalTo("text")))
                .withRequestBody(matchingJsonPath("$.to", equalTo("5491122223333")))
                .withRequestBody(matchingJsonPath("$.text.body", equalTo("Hola! Tu receta RX-1"))));
    }

    @Test
    void ante5xx_degradaAIntegrationUnavailable() {
        wm.stubFor(post(urlEqualTo("/PHONE1/messages"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> sender().send("+5491122223333", "x"))
                .isInstanceOf(IntegrationUnavailableException.class);
    }

    @Test
    void ante4xx_propaga() {
        wm.stubFor(post(urlEqualTo("/PHONE1/messages"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"invalid\"}}")));

        assertThatThrownBy(() -> sender().send("+5491122223333", "x"))
                .isInstanceOf(HttpClientErrorException.class);
    }
}
