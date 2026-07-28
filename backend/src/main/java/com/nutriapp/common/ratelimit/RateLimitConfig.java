package com.nutriapp.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el {@link RateLimitFilter} sólo para los endpoints públicos POST.
 * Se ordena DESPUÉS de la cadena de Spring Security (order -100) a propósito: así el CorsFilter de
 * security ya corrió y la respuesta 429 lleva los headers CORS → el SPA puede leer el mensaje del 429
 * en {@code /registro} (si corriera antes de CORS, el browser lo bloquearía como error de red).
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimiterService limiter, ObjectMapper objectMapper, RateLimitProperties props) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(limiter, objectMapper, props));
        registration.addUrlPatterns("/api/v1/registro", "/api/v1/webhooks/*");
        registration.setOrder(0); // > -100 (Spring Security) → corre después, con CORS ya aplicado
        return registration;
    }
}
