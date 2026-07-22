package com.nutriapp.modules.receta.service;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.tiendanube.TiendaNubeClient;
import com.nutriapp.modules.notificacion.service.NotificacionService;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.paciente.mapper.PacienteMapper;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.mapper.ProductoMapper;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import com.nutriapp.modules.receta.RecetaProperties;
import com.nutriapp.modules.receta.dto.RecetaCreateRequest;
import com.nutriapp.modules.receta.dto.RecetaResponse;
import com.nutriapp.modules.receta.entity.CuponSyncEstado;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.entity.RecetaItem;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecetaService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final RecetaRepository repo;
    private final PacienteRepository pacienteRepository;
    private final ProductoRepository productoRepository;
    private final PacienteMapper pacienteMapper;
    private final ProductoMapper productoMapper;
    private final NutricionistaService nutricionistaService;
    private final NotificacionService notificacionService;
    private final CodigoGenerator codigoGenerator;
    private final TiendaNubeClient tiendaNubeClient;
    private final RecetaProperties props;

    @Transactional(readOnly = true)
    public Page<RecetaResponse> search(EstadoReceta estado, Pageable pageable) {
        UUID nutriId = nutricionistaService.getCurrent().getId();
        return repo.search(nutriId, estado, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RecetaResponse get(UUID id) {
        return toResponseDetalle(getOwned(id));
    }

    /**
     * Emisión de receta. En Fase 0 crea la receta + items + código único e intenta registrar
     * el cupón en TiendaNube; en modo stub la excepción se captura y el cupón queda PENDIENTE
     * de sync (la receta se emite igual). Notificaciones (mail/WhatsApp) y webhook: Fase 1.
     */
    @Transactional
    public RecetaResponse emitir(RecetaCreateRequest req) {
        Nutricionista nutri = nutricionistaService.getCurrentAprobado();

        Paciente paciente = pacienteRepository
                .findByIdAndNutricionistaIdAndDeletedAtIsNull(req.pacienteId(), nutri.getId())
                .orElseThrow(() -> new NotFoundException("Paciente no encontrado"));

        if (req.items().size() > props.maxItems()) {
            throw new ConflictException("La receta admite hasta " + props.maxItems() + " producto(s) por ahora");
        }

        Receta receta = new Receta();
        receta.setCodigo(generarCodigoUnico());
        receta.setNutricionistaId(nutri.getId());
        receta.setPacienteId(paciente.getId());
        receta.setEstado(EstadoReceta.PENDIENTE);
        receta.setDescuentoPct(req.descuentoPct() != null ? req.descuentoPct() : props.descuentoDefaultPct());
        Instant now = Instant.now();
        receta.setEmitidaAt(now);
        receta.setVenceAt(LocalDate.now(AR).plusDays(props.vigenciaDias()));

        for (RecetaCreateRequest.Item itemReq : req.items()) {
            Producto producto = productoRepository.findById(itemReq.productoId())
                    .filter(p -> !p.isDeleted())
                    .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + itemReq.productoId()));
            RecetaItem item = new RecetaItem();
            item.setProductoId(producto.getId());
            item.setCantidad(itemReq.cantidad());
            item.setPrecioLista(producto.getPrecio());
            item.setIndicaciones(itemReq.indicaciones());
            receta.addItem(item);
        }

        registrarCupon(receta);

        Receta saved = repo.save(receta);
        // Encolar sólo inserta filas de notificación (misma tx, respeta la FK a recetas). El
        // desacople de las integraciones externas lo da el dispatcher async, NO este insert:
        // ninguna llamada a mail/WhatsApp/TiendaNube ocurre en la ruta de emisión.
        notificacionService.encolarEmisionReceta(saved, paciente);
        log.info("Receta {} emitida por nutri {} (cupon: {}) — notificaciones encoladas",
                saved.getCodigo(), nutri.getEmail(), saved.getCuponSyncEstado());
        return toResponseDetalle(saved);
    }

    /**
     * Anula una receta PENDIENTE: intenta borrar el cupón en TiendaNube (degrada si está
     * en stub/caída) y la marca ANULADA. Sólo el estado PENDIENTE es anulable — una receta
     * ya APLICADA/VENCIDA/ANULADA no se toca.
     */
    @Transactional
    public RecetaResponse anular(UUID id) {
        Receta receta = getOwned(id);
        if (receta.getEstado() != EstadoReceta.PENDIENTE) {
            throw new ConflictException("Sólo se pueden anular recetas pendientes (esta está "
                    + receta.getEstado().name().toLowerCase() + ")");
        }
        if (receta.getCuponTiendanubeId() != null) {
            try {
                tiendaNubeClient.deleteCoupon(receta.getCuponTiendanubeId());
            } catch (IntegrationUnavailableException ex) {
                log.info("No se pudo borrar el cupón {} de receta {} (integración no conectada): {}",
                        receta.getCuponTiendanubeId(), receta.getCodigo(), ex.getMessage());
            }
        }
        receta.setEstado(EstadoReceta.ANULADA);
        receta.setAnuladaAt(Instant.now());
        Receta saved = repo.save(receta);
        // Cancelar las notificaciones aún QUEUED: no queremos que salga un cupón ya invalidado.
        notificacionService.cancelarPendientes(saved.getId());
        log.info("Receta {} anulada", saved.getCodigo());
        return toResponseDetalle(saved);
    }

    /** Reenvía (reencola) las notificaciones de una receta PENDIENTE. */
    @Transactional
    public RecetaResponse reenviar(UUID id) {
        Receta receta = getOwned(id);
        if (receta.getEstado() != EstadoReceta.PENDIENTE) {
            throw new ConflictException("Sólo se pueden reenviar recetas pendientes (esta está "
                    + receta.getEstado().name().toLowerCase() + ")");
        }
        Paciente paciente = pacienteRepository.findById(receta.getPacienteId())
                .orElseThrow(() -> new NotFoundException("Paciente de la receta no encontrado"));
        notificacionService.reencolar(receta, paciente);
        log.info("Notificaciones de receta {} reencoladas", receta.getCodigo());
        return toResponseDetalle(receta);
    }

    /** Intenta crear el cupón en TiendaNube; degrada a PENDIENTE si la integración está en stub/caída. */
    private void registrarCupon(Receta receta) {
        List<Long> variantIds = receta.getItems().stream()
                .map(i -> productoRepository.findById(i.getProductoId()).orElse(null))
                .filter(p -> p != null && p.getTiendanubeVariantId() != null)
                .map(Producto::getTiendanubeVariantId)
                .toList();
        try {
            TiendaNubeClient.Coupon coupon = tiendaNubeClient.createCoupon(new TiendaNubeClient.CouponRequest(
                    receta.getCodigo(),
                    receta.getDescuentoPct(),
                    LocalDate.now(AR),
                    receta.getVenceAt(),
                    variantIds));
            receta.setCuponTiendanubeId(coupon.id());
            receta.setCuponSyncEstado(CuponSyncEstado.SINCRONIZADO);
        } catch (IntegrationUnavailableException ex) {
            receta.setCuponSyncEstado(CuponSyncEstado.PENDIENTE);
            receta.setCuponSyncError(ex.getMessage());
            log.info("Cupón de receta {} queda PENDIENTE de sync: {}", receta.getCodigo(), ex.getMessage());
        }
    }

    private String generarCodigoUnico() {
        for (int intento = 0; intento < 10; intento++) {
            String candidato = codigoGenerator.generar();
            if (!repo.existsByCodigo(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un código único de receta");
    }

    private Receta getOwned(UUID id) {
        UUID nutriId = nutricionistaService.getCurrent().getId();
        return repo.findByIdAndNutricionistaIdAndDeletedAtIsNull(id, nutriId)
                .orElseThrow(() -> new NotFoundException("Receta no encontrada"));
    }

    /**
     * Versión de lista (resumida): NO incluye notificaciones para evitar una query por receta
     * en `GET /recetas` y el dashboard. El front sólo muestra notificaciones en el detalle.
     */
    public RecetaResponse toResponse(Receta receta) {
        return build(receta, false);
    }

    /** Versión de detalle: incluye las notificaciones de la receta (`GET /recetas/{id}`). */
    public RecetaResponse toResponseDetalle(Receta receta) {
        return build(receta, true);
    }

    /** Ensambla el RecetaResponse resolviendo paciente y productos (batch para evitar N+1). */
    private RecetaResponse build(Receta receta, boolean conNotificaciones) {
        Paciente paciente = pacienteRepository.findById(receta.getPacienteId()).orElse(null);

        Map<UUID, Producto> productos = new LinkedHashMap<>();
        for (RecetaItem item : receta.getItems()) {
            productos.computeIfAbsent(item.getProductoId(),
                    pid -> productoRepository.findById(pid).orElse(null));
        }

        List<RecetaResponse.Item> items = receta.getItems().stream()
                .map(i -> new RecetaResponse.Item(
                        productos.get(i.getProductoId()) != null
                                ? productoMapper.toResponse(productos.get(i.getProductoId())) : null,
                        i.getCantidad(),
                        i.getPrecioLista(),
                        i.getIndicaciones()))
                .toList();

        RecetaResponse.Conversion conversion = null;
        if (receta.getEstado() == EstadoReceta.APLICADA) {
            conversion = new RecetaResponse.Conversion(
                    receta.getOrdenNumero(),
                    receta.getOrdenTotal(),
                    receta.getOrdenPaidAt(),
                    receta.getComisionPct(),
                    receta.getComisionMonto());
        }

        return new RecetaResponse(
                receta.getId(),
                receta.getCodigo(),
                receta.getEstado().name(),
                paciente != null ? pacienteMapper.toResponse(paciente) : null,
                items,
                receta.getDescuentoPct(),
                receta.getEmitidaAt(),
                receta.getVenceAt(),
                receta.getCuponSyncEstado().name(),
                conNotificaciones ? notificacionService.forReceta(receta.getId()) : null,
                conversion);
    }
}
