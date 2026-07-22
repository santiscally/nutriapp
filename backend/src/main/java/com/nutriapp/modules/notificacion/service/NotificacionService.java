package com.nutriapp.modules.notificacion.service;

import com.nutriapp.modules.notificacion.dto.NotificacionResponse;
import com.nutriapp.modules.notificacion.entity.CanalNotificacion;
import com.nutriapp.modules.notificacion.entity.EstadoNotificacion;
import com.nutriapp.modules.notificacion.entity.Notificacion;
import com.nutriapp.modules.notificacion.repository.NotificacionRepository;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.receta.entity.Receta;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, consulta y control de estado de las notificaciones de receta. El envío real (I/O de
 * mail/WhatsApp) lo hace el {@link NotificacionDispatcher} FUERA de transacción; acá viven
 * las operaciones de DB, cada una en su transacción corta. La emisión de receta sólo se acopla
 * al insert de estas filas (misma tx, respeta la FK), nunca a las integraciones externas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository repo;
    private final NotificacionTemplates templates;

    /** Encola EMAIL + WhatsApp para una receta recién emitida. Idempotente por (receta, canal). */
    @Transactional
    public void encolarEmisionReceta(Receta receta, Paciente paciente) {
        List<Notificacion> existentes = repo.findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(receta.getId());
        upsertQueued(existentes, receta, paciente, CanalNotificacion.EMAIL);
        upsertQueued(existentes, receta, paciente, CanalNotificacion.WHATSAPP);
    }

    /** Reenviar: vuelve a poner en cola (reset de intentos) reusando las filas por (receta, canal). */
    @Transactional
    public void reencolar(Receta receta, Paciente paciente) {
        encolarEmisionReceta(receta, paciente);
    }

    /**
     * Cancela (soft-delete) las notificaciones aún QUEUED de una receta. Se usa al anular:
     * evita que el dispatcher envíe un código de cupón que la anulación acaba de invalidar.
     * Las ya SENT no se tocan (no se pueden des-enviar).
     */
    @Transactional
    public void cancelarPendientes(UUID recetaId) {
        List<Notificacion> pendientes = repo.findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(recetaId).stream()
                .filter(n -> n.getEstado() == EstadoNotificacion.QUEUED)
                .toList();
        pendientes.forEach(Notificacion::softDelete);
        if (!pendientes.isEmpty()) {
            repo.saveAll(pendientes);
            log.info("Canceladas {} notificacion(es) QUEUED de la receta {}", pendientes.size(), recetaId);
        }
    }

    private void upsertQueued(List<Notificacion> existentes, Receta receta, Paciente paciente, CanalNotificacion canal) {
        Notificacion n = existentes.stream()
                .filter(x -> x.getCanal() == canal)
                .findFirst()
                .orElseGet(Notificacion::new);

        n.setRecetaId(receta.getId());
        n.setCanal(canal);
        if (canal == CanalNotificacion.EMAIL) {
            n.setDestinatario(paciente.getEmail());
            n.setAsunto(templates.asuntoEmail(receta));
            n.setCuerpo(templates.cuerpoEmail(receta, paciente));
        } else {
            n.setDestinatario(paciente.getWhatsapp());
            n.setAsunto(null);
            n.setCuerpo(templates.cuerpoWhatsApp(receta, paciente));
        }
        n.setEstado(EstadoNotificacion.QUEUED);
        n.setIntentos(0);
        n.setLastError(null);
        n.setSentAt(null);
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> forReceta(UUID recetaId) {
        return repo.findByRecetaIdAndDeletedAtIsNullOrderByCanalAsc(recetaId).stream()
                .map(n -> new NotificacionResponse(n.getCanal().name(), n.getEstado().name(), n.getSentAt()))
                .toList();
    }

    // --- Dispatch: se lee el lote acá (tx corta) y el envío ocurre FUERA de transacción ---

    /** Toma un lote de QUEUED (por debajo del tope de intentos) como datos desprendidos de la sesión. */
    @Transactional(readOnly = true)
    public List<NotificacionPendiente> tomarLote(int maxIntentos, int batchSize) {
        return repo.findByEstadoAndIntentosLessThanAndDeletedAtIsNullOrderByCreatedAtAsc(
                        EstadoNotificacion.QUEUED, maxIntentos, PageRequest.of(0, batchSize))
                .stream()
                .map(n -> new NotificacionPendiente(n.getId(), n.getCanal(),
                        n.getDestinatario(), n.getAsunto(), n.getCuerpo()))
                .toList();
    }

    @Transactional
    public void marcarEnviada(UUID id) {
        repo.findById(id).ifPresent(n -> {
            n.marcarEnviada();
            repo.save(n);
        });
    }

    /** Transitorio (stub/proveedor caído): NO consume intentos, sigue QUEUED. */
    @Transactional
    public void marcarSinConexion(UUID id, String error) {
        repo.findById(id).ifPresent(n -> {
            n.setLastError(error);
            repo.save(n);
        });
    }

    /** Error no transitorio: suma un intento; al superar el tope pasa a FAILED. */
    @Transactional
    public void marcarFallo(UUID id, String error, int maxIntentos) {
        repo.findById(id).ifPresent(n -> {
            n.registrarFallo(error);
            if (n.getIntentos() >= maxIntentos) {
                n.setEstado(EstadoNotificacion.FAILED);
                log.warn("Notificacion {} canal {} FAILED tras {} intentos: {}",
                        n.getId(), n.getCanal(), n.getIntentos(), error);
            }
            repo.save(n);
        });
    }

    /** Datos de una notificación a enviar, desprendidos de la sesión JPA. */
    public record NotificacionPendiente(
            UUID id, CanalNotificacion canal, String destinatario, String asunto, String cuerpo) {}
}
