package com.bonosapp.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * OAuth2 Resource Server contra Keycloak. Mapea dos fuentes de authorities:
 * <ul>
 *   <li>{@code realm_access.roles} → prefijo {@code ROLE_} (hasRole('ADMIN')).</li>
 *   <li>{@code resource_access.bonosapp-backend.roles} → sin prefijo
 *       (hasAuthority('recetas:write')).</li>
 * </ul>
 * Stateless, CORS delegado al bean de {@link CorsConfig}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String BACKEND_CLIENT_ID = "bonosapp-backend";

    @Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    /**
     * Cadena dedicada a swagger-ui/openapi. CSP relajado para que el bundle JS+CSS
     * de swagger cargue. En prod springdoc está deshabilitado y la cadena deniega todo.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
        if (!apiDocsEnabled) {
            http.securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
            return http.build();
        }
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.frameOptions(frame -> frame.sameOrigin());
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.referrerPolicy(ref -> ref.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; "
                            + "script-src 'self' 'unsafe-inline'; "
                            + "style-src 'self' 'unsafe-inline'; "
                            + "img-src 'self' data:; "
                            + "font-src 'self' data:; "
                            + "connect-src 'self'; "
                            + "frame-ancestors 'none'"));
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.frameOptions(frame -> frame.deny());
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.referrerPolicy(ref -> ref.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(p -> p.policy(
                            "geolocation=(), microphone=(), camera=(), payment=()"));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'none'; frame-ancestors 'none'"));
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/error",
                                // registro público de nutricionistas (Fase 1)
                                "/api/v1/registro",
                                // webhooks TiendaNube: la auth es la firma HMAC, no JWT (Fase 1)
                                "/api/v1/webhooks/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            authorities.addAll(new JwtGrantedAuthoritiesConverter().convert(jwt));

            // Realm roles → ROLE_<nombre>
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof Map<?, ?> realmMap) {
                Object roles = realmMap.get("roles");
                if (roles instanceof List<?> roleList) {
                    for (Object role : roleList) {
                        if (role != null) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        }
                    }
                }
            }

            // Client roles del bonosapp-backend → authority plano (e.g. recetas:write)
            Object resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess instanceof Map<?, ?> resourceMap) {
                Object clientAccess = resourceMap.get(BACKEND_CLIENT_ID);
                if (clientAccess instanceof Map<?, ?> clientMap) {
                    Object roles = clientMap.get("roles");
                    if (roles instanceof List<?> roleList) {
                        for (Object role : roleList) {
                            if (role != null) {
                                authorities.add(new SimpleGrantedAuthority(role.toString()));
                            }
                        }
                    }
                }
            }

            return authorities;
        });
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
