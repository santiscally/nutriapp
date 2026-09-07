package com.bonosapp.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bonosapp.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aplica el rate limit por IP a los endpoints públicos POST ({@code /registro}, {@code /webhooks/*}).
 * Los preflight OPTIONS y cualquier GET pasan sin consumir tokens. Al superar el límite responde
 * 429 con el {@link ApiError} uniforme + cabecera {@code Retry-After}, para que el frontend surfacee
 * el mensaje igual que el resto de los errores.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService limiter;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties props;

    public RateLimitFilter(RateLimiterService limiter, ObjectMapper objectMapper, RateLimitProperties props) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Sólo se limita el POST: los preflight OPTIONS y los GET no consumen cupo.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String bucket = bucketFor(request.getRequestURI());
        if (bucket == null || limiter.tryAcquire(bucket, clientId(request))) {
            chain.doFilter(request, response);
            return;
        }
        tooManyRequests(request, response, bucket);
    }

    private String bucketFor(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.startsWith("/api/v1/webhooks")) {
            return RateLimitProperties.BUCKET_WEBHOOKS;
        }
        if (uri.equals("/api/v1/registro")) {
            return RateLimitProperties.BUCKET_REGISTRO;
        }
        return null;
    }

    /** IP del cliente: primer hop de {@code X-Forwarded-For} (lo setea nginx en prod) o el remote addr. */
    private String clientId(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private void tooManyRequests(HttpServletRequest request, HttpServletResponse response, String bucket)
            throws IOException {
        RateLimitProperties.Bucket cfg = props.bucketFor(bucket);
        int retryAfter = cfg != null ? cfg.windowSeconds() : 60;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED",
                "Demasiadas solicitudes. Esperá unos segundos e intentá de nuevo.",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
