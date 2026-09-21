package com.bonosapp.modules.receta.service;

import com.bonosapp.common.error.ConflictException;
import com.bonosapp.common.error.NotFoundException;
import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient;
import com.bonosapp.modules.nutricionista.service.ParametrosNegocioService;
import com.bonosapp.modules.notificacion.service.NotificacionService;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.service.NutricionistaService;
import com.bonosapp.modules.paciente.entity.Paciente;
import com.bonosapp.modules.paciente.mapper.PacienteMapper;
import com.bonosapp.modules.paciente.repository.PacienteRepository;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.mapper.ProductoMapper;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.receta.RecetaProperties;
import com.bonosapp.modules.receta.dto.RecetaCreateRequest;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.entity.RecetaItem;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final CuponSyncService cuponSyncService;
    private final RecetaProperties props;
    private final ParametrosNegocioService parametrosNegocioService;
    private final WaMeLinkBuilder waMeLinkBuilder;

    @Transactional(readOnly = true)
    public Page<RecetaResponse> search(EstadoReceta estado, UUID pacienteId, String q,
                                      LocalDate desde, LocalDate hasta, Pageable pageable) {
        UUID nutriId = nutricionistaService.getCurrent().getId();
        return repo.search(nutriId, estado, pacienteId, q, desdeInclusive(desde), hastaInclusive(hasta),
                pageable).map(this::toResponse);
    }

    /** Ventana [desde, hasta] en horario argentino, con los dos extremos inclusive. */
    public static Instant desdeInclusive(LocalDate desde) {
        return desde == null ? null : desde.atStartOfDay(AR).toInstant();
    }

    public static Instant hastaInclusive(LocalDate hasta) {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay(AR).toInstant();
    }

    @Transactional(readOnly = true)
    public RecetaResponse get(UUID id) {
        return toResponseDetalle(getOwned(id));
    }

    /**
     * Emisión de receta. En Fase 0 crea la receta + items + código único e intenta registrar
     * el cupón en TiendaNube; en modo stub la excepción se captura y el cupón queda PENDIENTE
     * de sync (la receta se emite igual). Notificaciones (mail) y webhook: Fase 1.
     */
    @Transactional
    public RecetaResponse emitir(RecetaCreateRequest req) {
        Nutricionista nutri = nutricionistaService.getCurrentAprobado();

        Paciente paciente = pacienteRepository
                .findByIdAndNutricionistaIdAndDeletedAtIsNull(req.pacienteId(), nutri.getId())
                .orElseThrow(() -> new NotFoundException("Paciente no encontrado"));

        if (req.items().size() > props.maxItems()) {
            throw new ConflictException("El bono profesional admite hasta " + props.maxItems() + " producto(s) por ahora");
        }

        Receta receta = new Receta();
        receta.setCodigo(generarCodigoUnico());
        receta.setNutricionistaId(nutri.getId());
        receta.setPacienteId(paciente.getId());
        receta.setEstado(EstadoReceta.PENDIENTE);
        Instant now = Instant.now();
        receta.setEmitidaAt(now);
        receta.setVenceAt(LocalDate.now(AR).plusDays(props.vigenciaDias()));

        List<Producto> productos = new ArrayList<>();
        for (RecetaCreateRequest.Item itemReq : req.items()) {
            Producto producto = productoRepository.findById(itemReq.productoId())
                    .filter(p -> !p.isDeleted())
                    .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + itemReq.productoId()));
            productos.add(producto);
            RecetaItem item = new RecetaItem();
            item.setProductoId(producto.getId());
            item.setCantidad(itemReq.cantidad());
            item.setPrecioLista(producto.getPrecio());
            item.setIndicaciones(itemReq.indicaciones());
            receta.addItem(item);
        }
        // Se snapshotea acá: cambiar el % después no reescribe los bonos ya emitidos.
        receta.setDescuentoPct(descuentoDe(productos, nutri));

        cuponSyncService.registrar(receta);

        Receta saved = repo.save(receta);
        // Encolar sólo inserta filas de notificación (misma tx, respeta la FK a recetas). El
        // desacople de las integraciones externas lo da el dispatcher async, NO este insert:
        // ninguna llamada a mail/TiendaNube ocurre en la ruta de emisión.
        notificacionService.encolarEmisionReceta(saved, paciente);
        log.info("Receta {} emitida por nutri {} (cupon: {}) — notificaciones encoladas",
                saved.getCodigo(), nutri.getEmail(), saved.getCuponSyncEstado());
        return toResponseDetalle(saved);
    }

    /**
     * S-02: el descuento es del producto, no de la profesional. Mientras el maestro no esté
     * importado ningún producto lo tiene cargado y cae al % de ella, que es como venía funcionando.
     *
     * <p>Dos productos con % distintos no se pueden emitir juntos: el cupón de TiendaNube es un
     * solo porcentaje y no hay forma de honrar los dos.
     */
    private BigDecimal descuentoDe(List<Producto> productos, Nutricionista nutri) {
        List<BigDecimal> distintos = new ArrayList<>();
        for (Producto p : productos) {
            BigDecimal pct = p.getDescuentoPct();
            if (pct != null && distintos.stream().noneMatch(d -> d.compareTo(pct) == 0)) {
                distintos.add(pct);
            }
        }
        if (distintos.size() > 1) {
            throw new ConflictException(
                    "Un bono no puede combinar productos con distinto % de descuento");
        }
        return distintos.isEmpty() ? parametrosNegocioService.descuentoPctDe(nutri) : distintos.get(0);
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
            throw new ConflictException("Sólo se pueden anular bonos pendientes (este está "
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
            throw new ConflictException("Sólo se pueden reenviar bonos pendientes (este está "
                    + receta.getEstado().name().toLowerCase() + ")");
        }
        Paciente paciente = pacienteRepository.findById(receta.getPacienteId())
                .orElseThrow(() -> new NotFoundException("Paciente del bono no encontrado"));
        notificacionService.reencolar(receta, paciente);
        log.info("Notificaciones de receta {} reencoladas", receta.getCodigo());
        return toResponseDetalle(receta);
    }

    private String generarCodigoUnico() {
        for (int intento = 0; intento < 10; intento++) {
            String candidato = codigoGenerator.generar();
            if (!repo.existsByCodigo(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un código único de bono profesional");
    }

    private Receta getOwned(UUID id) {
        UUID nutriId = nutricionistaService.getCurrent().getId();
        return repo.findByIdAndNutricionistaIdAndDeletedAtIsNull(id, nutriId)
                .orElseThrow(() -> new NotFoundException("Bono no encontrado"));
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
                        i.getIndicaciones()))
                .toList();

        // C-05: LIQUIDADA también convirtió — tiene que seguir mostrando su conversión.
        RecetaResponse.Conversion conversion = null;
        if (receta.getEstado().esConvertida()) {
            conversion = new RecetaResponse.Conversion(
                    receta.getOrdenNumero(),
                    receta.getOrdenPaidAt(),
                    receta.getComisionPct(),
                    receta.getComisionMonto(),
                    receta.getLiquidadaAt());
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
                receta.getCuponSyncEstado().mensajeDegradacion(receta.getCuponSyncError()),
                waMeLinkBuilder.forReceta(receta, paciente),
                conNotificaciones ? notificacionService.forReceta(receta.getId()) : null,
                conversion);
    }
}
