package com.nutriapp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.modules.nutricionista.entity.EstadoValidacion;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.producto.entity.OrigenProducto;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import com.nutriapp.modules.webhook.service.TiendaNubeWebhookService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Flujo núcleo end-to-end contra la DB real: emisión de receta (descuento fijo de config) →
 * webhook simulado {@code order/paid} (conversión con comisión de config) → receta APLICADA →
 * cierre mensual reflejando la comisión.
 */
class RecetaFlowIT extends PostgresITBase {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired NutricionistaRepository nutricionistaRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired ProductoRepository productoRepository;
    @Autowired TiendaNubeWebhookService webhookService;

    private final String sub = UUID.randomUUID().toString();
    private final String email = "it-" + sub + "@test.dev";
    private UUID pacienteId;
    private UUID productoId;

    @BeforeEach
    void seed() {
        Nutricionista nutri = new Nutricionista();
        nutri.setNombre("Test");
        nutri.setApellido("Nutri");
        nutri.setEmail(email);
        nutri.setEstadoValidacion(EstadoValidacion.APROBADA);
        nutri.setKeycloakUserId(sub);
        // V011: los % son propios de cada nutricionista y obligatorios (ya no hay global al que caer).
        // De estos dos salen el descuento de la receta emitida y la comisión de la conversión.
        nutri.setDescuentoPct(new BigDecimal("15.00"));
        nutri.setComisionPct(new BigDecimal("10.00"));
        nutri.setActivo(true);
        nutri = nutricionistaRepository.save(nutri);

        Paciente p = new Paciente();
        p.setNutricionistaId(nutri.getId());
        p.setNombre("Ana");
        p.setApellido("López");
        p.setEmail("ana-" + sub + "@test.dev");
        p.setWhatsapp("+5491100000000");
        pacienteId = pacienteRepository.save(p).getId();

        // El producto lo crea el test. Antes se tomaba el primero del seed de `V003`, pero ese seed se
        // vació el 2026-07-28 (el catálogo se puebla sincronizando Contabilium, no con datos inventados)
        // y el IT quedó dependiendo de una fila que ya no existe. Creándolo acá el test no depende de
        // ninguna migración de datos.
        Producto prod = new Producto();
        prod.setOrigen(OrigenProducto.CONTABILIUM);
        prod.setSku("IT-" + sub.substring(0, 8));
        prod.setNombre("Producto de test");
        prod.setPrecio(new BigDecimal("10000.00"));
        prod.setStock(50);
        prod.setPublicado(true);
        productoId = productoRepository.save(prod).getId();
    }

    private RequestPostProcessor nutriJwt() {
        return jwt()
                .jwt(j -> j.subject(sub).claim("email", email))
                .authorities(
                        new SimpleGrantedAuthority("recetas:write"),
                        new SimpleGrantedAuthority("recetas:read"),
                        new SimpleGrantedAuthority("dashboard:read"));
    }

    private JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void emision_a_conversion_a_cierre() throws Exception {
        // 1. Emitir. El % de descuento NO viaja en el request: lo aplica el back desde config (seed 15%).
        String body = objectMapper.writeValueAsString(Map.of(
                "pacienteId", pacienteId.toString(),
                "items", List.of(Map.of("productoId", productoId.toString(), "cantidad", 1))));

        MvcResult emitRes = mockMvc.perform(post("/api/v1/recetas")
                        .with(nutriJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode receta = json(emitRes);
        UUID recetaId = UUID.fromString(receta.get("id").asText());
        String codigo = receta.get("codigo").asText();
        assertThat(receta.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(receta.get("descuentoPct").decimalValue()).isEqualByComparingTo("15.00");
        // En stub el cupón no sincroniza: queda PENDIENTE (degradación esperada, no falla la emisión).
        assertThat(receta.get("cuponSyncEstado").asText()).isEqualTo("PENDIENTE");

        // 2. Simular el webhook order/paid con el cupón de la receta → conversión (comisión config 10%).
        TiendaNubeClient.Order orden = new TiendaNubeClient.Order(
                7001L, 512, new BigDecimal("1000.00"), "paid", Instant.now(),
                List.of(new TiendaNubeClient.OrderCoupon(1L, codigo)));
        int aplicadas = webhookService.aplicarOrden(orden);
        assertThat(aplicadas).isEqualTo(1);

        // 3. La receta quedó APLICADA con el snapshot de la orden + la comisión.
        MvcResult detRes = mockMvc.perform(get("/api/v1/recetas/{id}", recetaId).with(nutriJwt()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode det = json(detRes);
        assertThat(det.get("estado").asText()).isEqualTo("APLICADA");
        JsonNode conv = det.get("conversion");
        assertThat(conv.get("comisionPct").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(conv.get("comisionMonto").decimalValue()).isEqualByComparingTo("100.00");
        // La nutricionista ve lo que gana, no lo que la tienda facturó: el total de la orden no
        // viaja en su response (sí en el cierre consolidado del admin, que es con lo que liquida).
        assertThat(conv.has("ordenTotal")).isFalse();

        // 4. El cierre mensual del mes en curso refleja la conversión.
        YearMonth ym = YearMonth.now(AR);
        MvcResult cierreRes = mockMvc.perform(get("/api/v1/dashboard/cierre-mensual")
                        .param("year", String.valueOf(ym.getYear()))
                        .param("month", String.valueOf(ym.getMonthValue()))
                        .with(nutriJwt()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cierre = json(cierreRes);
        assertThat(cierre.get("recetasEmitidas").asInt()).isEqualTo(1);
        assertThat(cierre.get("recetasAplicadas").asInt()).isEqualTo(1);
        assertThat(cierre.get("comisionTotal").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(cierre.get("detalle").get(0).get("recetaCodigo").asText()).isEqualTo(codigo);
    }
}
