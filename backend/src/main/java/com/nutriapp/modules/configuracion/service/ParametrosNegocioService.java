package com.nutriapp.modules.configuracion.service;

import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-01 (post-demo 2026-07-31) — resuelve el % de descuento y de comisión que aplica a una
 * nutricionista: <b>el suyo propio si lo tiene, y si no el global</b> de {@code configuracion_sistema}.
 *
 * <p>Único punto donde se decide esto. Ni {@code RecetaService} ni el webhook deberían volver a
 * leer un porcentaje directo de la configuración global: si lo hacen, se saltean el override por
 * nutricionista y los cálculos quedan inconsistentes entre emisión y conversión.
 *
 * <p>Los valores se <b>snapshotean</b> en la receta al momento de emitir (descuento) y de convertir
 * (comisión), así cambiar un % no reescribe la historia ni rompe los cierres ya cerrados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParametrosNegocioService {

    private final ConfiguracionService configuracionService;
    private final NutricionistaRepository nutricionistaRepository;

    /** % de descuento de la receta. Override de la nutricionista → global. */
    public BigDecimal descuentoPctDe(Nutricionista nutri) {
        return nutri != null && nutri.getDescuentoPct() != null
                ? nutri.getDescuentoPct()
                : configuracionService.getDescuentoPct();
    }

    /** % de comisión de la nutricionista. Override propio → global. */
    public BigDecimal comisionPctDe(Nutricionista nutri) {
        return nutri != null && nutri.getComisionPct() != null
                ? nutri.getComisionPct()
                : configuracionService.getComisionPct();
    }

    /**
     * Variante por id, para el camino de conversión (el webhook tiene la receta, no la entidad).
     * Si la nutricionista no aparece, cae al global en vez de romper: perder una comisión por un
     * dato faltante sería peor que usar el valor por defecto.
     */
    @Transactional(readOnly = true)
    public BigDecimal comisionPctDe(UUID nutricionistaId) {
        if (nutricionistaId == null) {
            return configuracionService.getComisionPct();
        }
        Nutricionista nutri = nutricionistaRepository.findById(nutricionistaId).orElse(null);
        if (nutri == null) {
            log.warn("[parametros] nutricionista {} no encontrada al resolver la comisión; uso el global",
                    nutricionistaId);
        }
        return comisionPctDe(nutri);
    }
}
