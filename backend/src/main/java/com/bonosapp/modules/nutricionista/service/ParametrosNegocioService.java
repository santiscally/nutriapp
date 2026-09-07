package com.bonosapp.modules.nutricionista.service;

import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el % de descuento y de comisión que aplica a una nutricionista.
 *
 * <p>Desde la migración {@code V011} cada nutricionista tiene los suyos y son obligatorios: no hay
 * global ni fallback. Antes (C-01) el campo era nullable y {@code null} significaba "usá el valor de
 * {@code configuracion_sistema}", lo que dejaba el mismo dato en dos lugares y hacía que cualquier
 * lectura que se salteara este servicio devolviera un número distinto al de la emisión.
 *
 * <p>Sigue siendo el único punto donde se decide esto, porque los valores se <b>snapshotean</b> en
 * la receta al emitir (descuento) y al convertir (comisión): cambiar un % no reescribe la historia
 * ni mueve los cierres ya cerrados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParametrosNegocioService {

    private final NutricionistaRepository nutricionistaRepository;

    /** % de descuento de las recetas de esta nutricionista. */
    public BigDecimal descuentoPctDe(Nutricionista nutri) {
        return nutri != null ? nutri.getDescuentoPct() : BigDecimal.ZERO;
    }

    /** % de comisión de esta nutricionista. */
    public BigDecimal comisionPctDe(Nutricionista nutri) {
        return nutri != null ? nutri.getComisionPct() : BigDecimal.ZERO;
    }

    /**
     * Variante por id, para el camino de conversión (el webhook tiene la receta, no la entidad).
     *
     * <p>Si la nutricionista no aparece devuelve 0 y deja un warn: sin global al que caer, cualquier
     * otro número sería inventado, y una comisión inventada sobre plata real es peor que una en cero
     * que salta a la vista en el cierre. No debería pasar — borrar una nutricionista con recetas
     * está bloqueado justamente por esto.
     */
    @Transactional(readOnly = true)
    public BigDecimal comisionPctDe(UUID nutricionistaId) {
        if (nutricionistaId == null) {
            log.warn("[parametros] receta sin nutricionista al resolver la comisión; queda en 0");
            return BigDecimal.ZERO;
        }
        Nutricionista nutri = nutricionistaRepository.findById(nutricionistaId).orElse(null);
        if (nutri == null) {
            log.warn("[parametros] nutricionista {} no encontrada al resolver la comisión; queda en 0",
                    nutricionistaId);
        }
        return comisionPctDe(nutri);
    }
}
