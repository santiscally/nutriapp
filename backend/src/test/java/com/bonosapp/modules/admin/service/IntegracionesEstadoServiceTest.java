package com.bonosapp.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.integrations.IntegrationsProperties.Contabilium;
import com.bonosapp.integrations.IntegrationsProperties.Mail;
import com.bonosapp.integrations.IntegrationsProperties.TiendaNube;
import com.bonosapp.integrations.health.IntegrationHealthRegistry;
import com.bonosapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.bonosapp.modules.admin.dto.IntegracionEstadoResponse;
import com.bonosapp.modules.admin.dto.IntegracionesEstadoResponse;
import com.bonosapp.modules.notificacion.entity.CanalNotificacion;
import com.bonosapp.modules.notificacion.entity.EstadoNotificacion;
import com.bonosapp.modules.notificacion.repository.NotificacionRepository;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.producto.service.ProductoSyncService;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntegracionesEstadoServiceTest {

    @Mock RecetaRepository recetaRepository;
    @Mock NotificacionRepository notificacionRepository;
    @Mock ProductoRepository productoRepository;
    @Mock ProductoSyncService productoSyncService;

    private final IntegrationHealthRegistry health = new IntegrationHealthRegistry();

    private IntegracionesEstadoService service(String contabiliumMode, String tiendanubeMode,
                                               String mailMode) {
        IntegrationsProperties props = new IntegrationsProperties(
                new Contabilium(contabiliumMode, "", "", ""),
                new TiendaNube(tiendanubeMode, "", "", "", "", "", "", "", ""),
                new Mail(mailMode, "", ""));
        return new IntegracionesEstadoService(
                props, health, recetaRepository, notificacionRepository, productoRepository, productoSyncService);
    }

    private IntegracionEstadoResponse porProveedor(IntegracionesEstadoResponse resp, String proveedor) {
        return resp.integraciones().stream()
                .filter(i -> i.proveedor().equals(proveedor))
                .findFirst().orElseThrow();
    }

    @Test
    void estado_stub_disponibleFalseYPendientesDesdeLaDb() {
        Instant ultimaSyncCatalogo = Instant.parse("2026-07-20T10:00:00Z");
        when(recetaRepository.countResyncables()).thenReturn(3L);
        when(notificacionRepository.countByEstadoAndCanalAndDeletedAtIsNull(
                EstadoNotificacion.QUEUED, CanalNotificacion.EMAIL)).thenReturn(2L);
        when(productoRepository.maxLastSyncedAt()).thenReturn(ultimaSyncCatalogo);

        IntegracionesEstadoResponse resp = service("stub", "stub", "stub").estado();

        // 2.4: whatsapp ya no es una integración (es un link manual) → quedan 3.
        assertThat(resp.integraciones()).hasSize(3);

        IntegracionEstadoResponse tn = porProveedor(resp, "tiendanube");
        assertThat(tn.modo()).isEqualTo("stub");
        assertThat(tn.disponible()).isFalse();
        assertThat(tn.pendientes()).isEqualTo(3L);

        assertThat(porProveedor(resp, "mail").pendientes()).isEqualTo(2L);

        IntegracionEstadoResponse cb = porProveedor(resp, "contabilium");
        assertThat(cb.pendientes()).isZero();
        assertThat(cb.ultimaSync()).isEqualTo(ultimaSyncCatalogo);
    }

    @Test
    void estado_live_disponibleSegunUltimoResultado() {
        IntegracionEstadoResponse antes = porProveedor(
                service("stub", "live", "stub").estado(), "tiendanube");
        assertThat(antes.modo()).isEqualTo("live");
        assertThat(antes.disponible()).isNull(); // live pero sin interacción todavía

        health.registrarExito(Proveedor.TIENDANUBE);
        assertThat(porProveedor(service("stub", "live", "stub").estado(), "tiendanube")
                .disponible()).isTrue();

        health.registrarError(Proveedor.TIENDANUBE, "timeout");
        IntegracionEstadoResponse caido = porProveedor(
                service("stub", "live", "stub").estado(), "tiendanube");
        assertThat(caido.disponible()).isFalse();
        assertThat(caido.ultimoError()).isEqualTo("timeout");
        assertThat(caido.ultimoErrorAt()).isNotNull();
    }
}
