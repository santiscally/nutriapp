package com.nutriapp.integrations.whatsapp;

import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.IntegrationsProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Envío de WhatsApp por Meta Cloud API. Se registra sólo si {@code WHATSAPP_MODE=live}
 * (ver {@code IntegrationsConfig}); la conexión real es Fase 2 (tarea 2.4: WABA + número +
 * template aprobado por Meta — el trámite tarda, se inicia en Fase 1).
 *
 * <p>⚠️ Hoy manda un mensaje de <b>tipo texto</b> ({@code POST /{phone-number-id}/messages}). Un
 * mensaje texto iniciado por el negocio sólo entra dentro de la ventana de 24h; fuera de ella Meta
 * exige un <b>template</b> pre-aprobado. Cuando el template esté aprobado (2.4), el port
 * {@link WhatsAppSender#send} deberá pasar parámetros estructurados en vez de texto libre — se
 * revisa entonces. Un 5xx/red caída degrada como el stub ({@link IntegrationUnavailableException});
 * un 4xx (número inválido, sin ventana) se propaga y el dispatcher lo cuenta como intento.
 */
@Slf4j
public class CloudApiWhatsAppSender implements WhatsAppSender {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final IntegrationsProperties.WhatsApp props;
    private final RestClient http;

    public CloudApiWhatsAppSender(IntegrationsProperties.WhatsApp props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.accessToken())
                .build();
    }

    @Override
    public void send(String toE164, String body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", normalize(toE164));
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", false, "body", body));
        try {
            http.post()
                    .uri("/{phoneNumberId}/messages", props.phoneNumberId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("[cloud-whatsapp] enviado a={}", toE164);
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            log.warn("[cloud-whatsapp] no disponible: {}", ex.getMessage());
            throw new IntegrationUnavailableException("whatsapp");
        }
    }

    /** Cloud API espera el número en E.164 sin el {@code +} inicial. */
    private static String normalize(String toE164) {
        if (toE164 == null) {
            return null;
        }
        return toE164.startsWith("+") ? toE164.substring(1) : toE164;
    }
}
