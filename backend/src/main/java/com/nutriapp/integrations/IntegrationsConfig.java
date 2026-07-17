package com.nutriapp.integrations;

import com.nutriapp.integrations.contabilium.ContabiliumClient;
import com.nutriapp.integrations.contabilium.StubContabiliumClient;
import com.nutriapp.integrations.mail.MailSender;
import com.nutriapp.integrations.mail.StubMailSender;
import com.nutriapp.integrations.tiendanube.StubTiendaNubeClient;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.integrations.whatsapp.StubWhatsAppSender;
import com.nutriapp.integrations.whatsapp.WhatsAppSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el adapter de cada integración según `nutriapp.integrations.<x>.mode`.
 * Las impls live llegan en Fase 1.6/2 — pedir `live` antes de eso corta el arranque
 * con un error claro (mejor que un stub silencioso haciéndose pasar por real).
 */
@Configuration
public class IntegrationsConfig {

    @Bean
    public TiendaNubeClient tiendaNubeClient(IntegrationsProperties props) {
        if (IntegrationsProperties.isLive(props.tiendanube().mode())) {
            throw new IllegalStateException(
                    "TIENDANUBE_MODE=live pero HttpTiendaNubeClient aún no está implementado (Fase 2)");
        }
        return new StubTiendaNubeClient();
    }

    @Bean
    public ContabiliumClient contabiliumClient(IntegrationsProperties props) {
        if (IntegrationsProperties.isLive(props.contabilium().mode())) {
            throw new IllegalStateException(
                    "CONTABILIUM_MODE=live pero HttpContabiliumClient aún no está implementado (Fase 2)");
        }
        return new StubContabiliumClient();
    }

    // Nombre explícito distinto de "mailSender" para no chocar con el JavaMailSender
    // que autoconfigura spring-boot-starter-mail (bean 'mailSender').
    @Bean("nutriappMailSender")
    public MailSender nutriappMailSender(IntegrationsProperties props) {
        if (IntegrationsProperties.isLive(props.mail().mode())) {
            throw new IllegalStateException(
                    "MAIL_MODE=live pero SmtpMailSender aún no está implementado (Fase 2)");
        }
        return new StubMailSender();
    }

    @Bean
    public WhatsAppSender whatsAppSender(IntegrationsProperties props) {
        if (IntegrationsProperties.isLive(props.whatsapp().mode())) {
            throw new IllegalStateException(
                    "WHATSAPP_MODE=live pero el sender de Cloud API aún no está implementado (Fase 2)");
        }
        return new StubWhatsAppSender();
    }
}
