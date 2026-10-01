package com.bonosapp.modules.nutricionista.service;

import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resuelve el % de comisión de una nutricionista; el descuento es del producto (F-14). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParametrosNegocioService {

    private final NutricionistaRepository nutricionistaRepository;

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
