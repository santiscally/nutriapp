package com.nutriapp.modules.admin.service;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.modules.admin.dto.CierreConsolidadoResponse;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-06 — arma el cierre consolidado del admin: una fila por nutricionista con actividad en el
 * rango, con lo facturado, lo comisionado y lo que todavía está impago.
 *
 * <p>El rango es <b>configurable</b>, no mes calendario: Leo pidió poder cortar por el período que
 * quieran (55:49). Las fechas son inclusive de punta a punta y se interpretan en hora argentina —
 * "del 1 al 31" tiene que incluir todo el 31.
 */
@Service
@RequiredArgsConstructor
public class CierreConsolidadoService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Tope defensivo: un rango absurdo no debería poder barrer años de recetas de una. */
    private static final int RANGO_MAX_DIAS = 366;

    private final RecetaRepository recetaRepository;
    private final NutricionistaRepository nutricionistaRepository;

    @Transactional(readOnly = true)
    public CierreConsolidadoResponse consolidado(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ConflictException("Hay que indicar el rango de fechas (desde y hasta)");
        }
        if (hasta.isBefore(desde)) {
            throw new ConflictException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }
        if (desde.plusDays(RANGO_MAX_DIAS).isBefore(hasta)) {
            throw new ConflictException("El rango no puede superar " + RANGO_MAX_DIAS + " días");
        }

        Instant inicio = desde.atStartOfDay(AR).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(AR).toInstant(); // 'hasta' inclusive

        List<Receta> recetas = recetaRepository.findConvertidasEntreTodas(inicio, fin);

        Map<UUID, List<Receta>> porNutri = recetas.stream()
                .collect(Collectors.groupingBy(Receta::getNutricionistaId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, Nutricionista> nutris = nutricionistaRepository.findAllById(porNutri.keySet())
                .stream().collect(Collectors.toMap(Nutricionista::getId, Function.identity()));

        List<CierreConsolidadoResponse.Fila> filas = new ArrayList<>(porNutri.size());
        for (Map.Entry<UUID, List<Receta>> e : porNutri.entrySet()) {
            filas.add(fila(e.getKey(), nutris.get(e.getKey()), e.getValue()));
        }
        // Primero quien más comisión tiene pendiente: es a quien hay que pagarle.
        filas.sort(Comparator.comparing(CierreConsolidadoResponse.Fila::comisionPendiente).reversed()
                .thenComparing(f -> f.apellido() == null ? "" : f.apellido()));

        return new CierreConsolidadoResponse(desde, hasta, filas, totales(filas));
    }

    private CierreConsolidadoResponse.Fila fila(UUID nutriId, Nutricionista n, List<Receta> recetas) {
        BigDecimal facturado = BigDecimal.ZERO;
        BigDecimal comision = BigDecimal.ZERO;
        BigDecimal comisionPendiente = BigDecimal.ZERO;
        List<UUID> pendientes = new ArrayList<>();

        for (Receta r : recetas) {
            facturado = facturado.add(valor(r.getOrdenTotal()));
            comision = comision.add(valor(r.getComisionMonto()));
            if (r.getEstado() == EstadoReceta.APLICADA) { // todavía no liquidada
                comisionPendiente = comisionPendiente.add(valor(r.getComisionMonto()));
                pendientes.add(r.getId());
            }
        }

        return new CierreConsolidadoResponse.Fila(
                nutriId,
                n != null ? n.getNombre() : null,
                n != null ? n.getApellido() : null,
                n != null ? n.getEmail() : null,
                n != null ? n.getCuit() : null,
                recetas.size(),
                facturado,
                comision,
                pendientes.size(),
                comisionPendiente,
                pendientes);
    }

    private CierreConsolidadoResponse.Totales totales(List<CierreConsolidadoResponse.Fila> filas) {
        long recetas = 0;
        BigDecimal facturado = BigDecimal.ZERO;
        BigDecimal comision = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        for (CierreConsolidadoResponse.Fila f : filas) {
            recetas += f.recetas();
            facturado = facturado.add(f.facturado());
            comision = comision.add(f.comision());
            pendiente = pendiente.add(f.comisionPendiente());
        }
        return new CierreConsolidadoResponse.Totales(recetas, facturado, comision, pendiente);
    }

    private static BigDecimal valor(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
