package com.nutriapp.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriapp.common.ratelimit.RateLimitProperties.Bucket;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    // ObjectMapper con los módulos de fecha (jsr310) para serializar el Instant del ApiError,
    // igual que el ObjectMapper que Spring inyecta en producción.
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private RateLimitFilter filter(int capacidadRegistro) {
        RateLimitProperties props = new RateLimitProperties(true, new Bucket(capacidadRegistro, 60), new Bucket(120, 60));
        RateLimiterService limiter = new RateLimiterService(props, () -> 0L);
        return new RateLimitFilter(limiter, mapper, props);
    }

    private MockHttpServletRequest post(String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void primeraPasaSegundaBloqueaConMismaIp() throws Exception {
        RateLimitFilter filter = filter(1);

        MockHttpServletResponse res1 = new MockHttpServletResponse();
        FilterChain chain1 = mock(FilterChain.class);
        filter.doFilter(post("/api/v1/registro", "9.9.9.9"), res1, chain1);
        verify(chain1).doFilter(any(), any());
        assertThat(res1.getStatus()).isEqualTo(200);

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        FilterChain chain2 = mock(FilterChain.class);
        filter.doFilter(post("/api/v1/registro", "9.9.9.9"), res2, chain2);

        verify(chain2, never()).doFilter(any(), any());
        assertThat(res2.getStatus()).isEqualTo(429);
        assertThat(res2.getHeader("Retry-After")).isEqualTo("60");
        assertThat(res2.getContentAsString()).contains("RATE_LIMITED");
        assertThat(res2.getContentType()).contains("application/json");
    }

    @Test
    void ipsDistintasNoSePisan() throws Exception {
        RateLimitFilter filter = filter(1);

        MockHttpServletResponse resA = new MockHttpServletResponse();
        FilterChain chainA = mock(FilterChain.class);
        filter.doFilter(post("/api/v1/registro", "1.1.1.1"), resA, chainA);
        verify(chainA).doFilter(any(), any());

        MockHttpServletResponse resB = new MockHttpServletResponse();
        FilterChain chainB = mock(FilterChain.class);
        filter.doFilter(post("/api/v1/registro", "2.2.2.2"), resB, chainB);
        verify(chainB).doFilter(any(), any()); // otra IP no está bloqueada
    }

    @Test
    void preflightOptionsNoConsumeCupo() throws Exception {
        RateLimitFilter filter = filter(1);

        // agoto el cupo de la IP con un POST
        filter.doFilter(post("/api/v1/registro", "9.9.9.9"), new MockHttpServletResponse(), mock(FilterChain.class));

        // el OPTIONS de la misma IP igual pasa (no se limita)
        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", "/api/v1/registro");
        options.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(options, res, chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void usaElPrimerHopDeXForwardedFor() throws Exception {
        RateLimitFilter filter = filter(1);

        MockHttpServletRequest req1 = post("/api/v1/registro", "10.0.0.1"); // remote = proxy
        req1.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        FilterChain chain1 = mock(FilterChain.class);
        filter.doFilter(req1, new MockHttpServletResponse(), chain1);
        verify(chain1).doFilter(any(), any());

        // misma IP real (aunque venga por otro proxy) → bloqueada
        MockHttpServletRequest req2 = post("/api/v1/registro", "10.0.0.2");
        req2.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.2");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        FilterChain chain2 = mock(FilterChain.class);
        filter.doFilter(req2, res2, chain2);
        verify(chain2, never()).doFilter(any(), any());
        assertThat(res2.getStatus()).isEqualTo(429);
    }
}
