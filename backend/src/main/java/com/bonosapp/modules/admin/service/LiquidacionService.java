package com.bonosapp.modules.admin.service;

import com.bonosapp.modules.admin.dto.LiquidacionResponse;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-05 (post-demo 2026-07-31) — liquidación de comisiones.
 *
 * <p>Cuando el admin le paga a una nutricionista, las recetas de ese pago pasan de
 * {@code APLICADA} a {@code LIQUIDADA}. Desde ahí dejan de aparecer entre las pendientes de
 * liquidar, pero <b>siguen contando en los cierres históricos</b>: liquidar no deshace la
 * conversión, sólo registra que ya se pagó.
 *
 * <p>Idempotente: reenviar una receta ya liquidada no la vuelve a contar ni pisa su fecha.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidacionService {

    private final RecetaRepository recetaRepository;

    /** Recetas convertidas y todavía impagas en la ventana. {@code nutricionistaId} null = todas. */
    @Transactional(readOnly = true)
    public List<Receta> pendientesDeLiquidar(UUID nutricionistaId, Instant desde, Instant hasta) {
        return recetaRepository.findLiquidables(nutricionistaId, desde, hasta);
    }

    /**
     * Marca como liquidadas las recetas indicadas. Sólo se liquida lo que está {@code APLICADA};
     * cualquier otra cosa se omite con su motivo en la respuesta.
     */
    @Transactional
    public LiquidacionResponse liquidar(Collection<UUID> recetaIds) {
        Instant ahora = Instant.now();
        Set<UUID> pedidas = new HashSet<>(recetaIds);

        Map<UUID, Receta> encontradas = recetaRepository.findByIdInAndDeletedAtIsNull(pedidas)
                .stream().collect(Collectors.toMap(Receta::getId, Function.identity()));

        List<LiquidacionResponse.Omitida> omitidas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int liquidadas = 0;

        for (UUID id : pedidas) {
            Receta r = encontradas.get(id);
            if (r == null) {
                omitidas.add(new LiquidacionResponse.Omitida(id, null, "no existe"));
                continue;
            }
            if (r.getEstado() == EstadoReceta.LIQUIDADA) {
                omitidas.add(new LiquidacionResponse.Omitida(id, r.getCodigo(), "ya estaba liquidada"));
                continue;
            }
            if (r.getEstado() != EstadoReceta.APLICADA) {
                omitidas.add(new LiquidacionResponse.Omitida(
                        id, r.getCodigo(), "no convertida (está " + r.getEstado() + ")"));
                continue;
            }
            r.setEstado(EstadoReceta.LIQUIDADA);
            r.setLiquidadaAt(ahora);
            recetaRepository.save(r);
            liquidadas++;
            if (r.getComisionMonto() != null) {
                total = total.add(r.getComisionMonto());
            }
        }

        log.info("[liquidacion] {} receta(s) liquidadas por {} de comisión ({} omitidas)",
                liquidadas, total, omitidas.size());
        return new LiquidacionResponse(liquidadas, total, ahora, omitidas);
    }
}
