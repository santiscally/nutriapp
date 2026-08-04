package com.nutriapp.integrations;

import com.nutriapp.integrations.contabilium.ContabiliumClient;
import com.nutriapp.integrations.contabilium.HttpContabiliumClient;
import com.nutriapp.integrations.contabilium.StubContabiliumClient;
import com.nutriapp.integrations.mail.MailSender;
import com.nutriapp.integrations.mail.SmtpMailSender;
import com.nutriapp.integrations.mail.StubMailSender;
import com.nutriapp.integrations.tiendanube.HttpTiendaNubeClient;
import com.nutriapp.integrations.tiendanube.StubTiendaNubeClient;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Registra el adapter de cada integración según {@code nutriapp.integrations.<x>.mode}:
 * (WhatsApp ya no está: se manda por link {@code wa.me}, no por API — tarea 2.4.)
 * {@code stub} (default) o {@code live}. Flip por env sin tocar código de negocio (CLAUDE.md
 * "Regla de oro"). Las impls {@code Http*}/{@code Smtp*}/{@code CloudApi*} sólo se instancian en
 * modo {@code live}: en {@code stub} nunca se construyen (evita exigir credenciales/SMTP en dev).
 */
@Configuration
public class IntegrationsConfig {

    @Bean
    public TiendaNubeClient tiendaNubeClient(IntegrationsProperties props) {
        if (IntegrationsProperties.isLive(props.tiendanube().mode())) {
            return new HttpTiendaNubeClient(props.tiendanube());
        }
        return new StubTiendaNubeClient();
    }

    @Bean
    public ContabiliumClient contabiliumClient(IntegrationsProperties props) {
        if (IntegrationsProperties.isLive(props.contabilium().mode())) {
            return new HttpContabiliumClient(props.contabilium());
        }
        return new StubContabiliumClient();
    }

    // Nombre explícito distinto de "mailSender" para no chocar con el JavaMailSender
    // que autoconfigura spring-boot-starter-mail (bean 'mailSender').
    @Bean("nutriappMailSender")
    public MailSender nutriappMailSender(IntegrationsProperties props, ObjectProvider<JavaMailSender> javaMailSender) {
        if (IntegrationsProperties.isLive(props.mail().mode())) {
            JavaMailSender delegate = javaMailSender.getIfAvailable();
            if (delegate == null) {
                throw new IllegalStateException(
                        "MAIL_MODE=live pero no hay JavaMailSender: configurá MAIL_SMTP_HOST (spring.mail.host)");
            }
            return new SmtpMailSender(delegate, props.mail());
        }
        return new StubMailSender();
    }
}
