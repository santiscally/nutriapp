package com.nutriapp.modules.admin.service;

import com.nutriapp.integrations.IntegrationsProperties;
import com.nutriapp.integrations.health.IntegrationHealthRegistry;
import com.nutriapp.integrations.health.IntegrationHealthRegistry.Health;
import com.nutriapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.nutriapp.modules.admin.dto.IntegracionEstadoResponse;
import com.nutriapp.modules.admin.dto.IntegracionesEstadoResponse;
import com.nutriapp.modules.notificacion.entity.CanalNotificacion;
import com.nutriapp.modules.notificacion.entity.EstadoNotificacion;
import com.nutriapp.modules.notificacion.repository.NotificacionRepository;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el estado de las 4 integraciones externas para el panel de admin (tarea 2.7). Combina:
 * <ul>
 *   <li>modo (stub/live) de {@link IntegrationsProperties},</li>
 *   <li>trabajo pendiente durable desde la DB (cupones sin sync / notifs QUEUED),</li>
 *   <li>disponibilidad + último error/éxito efímeros desde {@link IntegrationHealthRegistry}.</li>
 * </ul>
 * En stub {@code disponible} es siempre false (no hay conexión por diseño); los mensajes de
 * degradación los da cada flujo de negocio, acá se consolida la foto para el admin.
 */
@Service
@RequiredArgsConstructor
public class IntegracionesEstadoService {

    private final IntegrationsProperties props;
    private final IntegrationHealthRegistry health;
    private final RecetaRepository recetaRepository;
    private final NotificacionRepository notificacionRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public IntegracionesEstadoResponse estado() {
        return new IntegracionesEstadoResponse(List.of(
                contabilium(),
                tiendanube(),
                mail(),
                whatsapp()));
    }

    private IntegracionEstadoResponse contabilium() {
        String modo = props.contabilium().mode();
        Health h = health.get(Proveedor.CONTABILIUM);
        // Contabilium es pull (el catálogo se lee bajo demanda), no encola trabajo: pendientes = 0.
        // Su "última sync" durable es la del catálogo, que sobrevive reinicios.
        return build("contabilium", modo, h, 0, productoRepository.maxLastSyncedAt());
    }

    private IntegracionEstadoResponse tiendanube() {
        String modo = props.tiendanube().mode();
        Health h = health.get(Proveedor.TIENDANUBE);
        long pendientes = recetaRepository.countResyncables();
        return build("tiendanube", modo, h, pendientes, h.ultimoExitoAt());
    }

    private IntegracionEstadoResponse mail() {
        String modo = props.mail().mode();
        Health h = health.get(Proveedor.MAIL);
        long pendientes = notificacionRepository.countByEstadoAndCanalAndDeletedAtIsNull(
                EstadoNotificacion.QUEUED, CanalNotificacion.EMAIL);
        return build("mail", modo, h, pendientes, h.ultimoExitoAt());
    }

    private IntegracionEstadoResponse whatsapp() {
        String modo = props.whatsapp().mode();
        Health h = health.get(Proveedor.WHATSAPP);
        long pendientes = notificacionRepository.countByEstadoAndCanalAndDeletedAtIsNull(
                EstadoNotificacion.QUEUED, CanalNotificacion.WHATSAPP);
        return build("whatsapp", modo, h, pendientes, h.ultimoExitoAt());
    }

    private IntegracionEstadoResponse build(String proveedor, String modo, Health h,
                                            long pendientes, java.time.Instant ultimaSync) {
        boolean live = IntegrationsProperties.isLive(modo);
        // En stub no hay conexión por diseño → disponible=false. En live lo inferimos del último resultado.
        Boolean disponible = live ? h.disponible() : Boolean.FALSE;
        return new IntegracionEstadoResponse(
                proveedor,
                live ? "live" : "stub",
                disponible,
                pendientes,
                h.ultimoError(),
                h.ultimoErrorAt(),
                ultimaSync);
    }
}
