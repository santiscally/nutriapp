package com.nutriapp.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.integrations.health.IntegrationHealthRegistry;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient.Webhook;
import com.nutriapp.modules.notificacion.NotificacionProperties;
import com.nutriapp.modules.webhook.dto.RegistrarWebhooksResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TiendaNubeWebhookRegistrarTest {

    private static final String URL = "https://bonosapp.com.ar/api/v1/webhooks/tiendanube";

    @Mock TiendaNubeClient tiendaNubeClient;
    @Mock IntegrationHealthRegistry health;

    private TiendaNubeWebhookRegistrar registrar(String appUrl) {
        NotificacionProperties props = new NotificacionProperties(0, 0, 0, "", appUrl);
        return new TiendaNubeWebhookRegistrar(tiendaNubeClient, props, health);
    }

    @Test
    void registraOrderPaidCuandoNoEstaba() {
        when(tiendaNubeClient.listWebhooks()).thenReturn(List.of());

        RegistrarWebhooksResponse r = registrar("https://bonosapp.com.ar").registrar();

        assertThat(r.creados()).containsExactly("order/paid");
        assertThat(r.yaEstaban()).isEmpty();
        assertThat(r.url()).isEqualTo(URL);
        verify(tiendaNubeClient).createWebhook("order/paid", URL);
    }

    @Test
    void noDuplicaLaSuscripcionSiYaEstabaRegistrada() {
        when(tiendaNubeClient.listWebhooks()).thenReturn(List.of(new Webhook(1L, "order/paid", URL)));

        RegistrarWebhooksResponse r = registrar("https://bonosapp.com.ar").registrar();

        assertThat(r.yaEstaban()).containsExactly("order/paid");
        assertThat(r.creados()).isEmpty();
        verify(tiendaNubeClient, never()).createWebhook(any(), any());
    }

    @Test
    void reregistraSiLaUrlApuntaAOtroLado() {
        when(tiendaNubeClient.listWebhooks())
                .thenReturn(List.of(new Webhook(1L, "order/paid", "https://viejo.example/api/v1/webhooks/tiendanube")));

        assertThat(registrar("https://bonosapp.com.ar").registrar().creados()).containsExactly("order/paid");
    }

    @Test
    void seNiegaSiLaUrlPublicaNoEsHttps() {
        assertThatThrownBy(() -> registrar("http://localhost:5173").registrar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        verify(tiendaNubeClient, never()).listWebhooks();
    }
}
