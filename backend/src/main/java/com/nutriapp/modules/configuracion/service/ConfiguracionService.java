package com.nutriapp.modules.configuracion.service;

import com.nutriapp.modules.configuracion.dto.ConfiguracionResponse;
import com.nutriapp.modules.configuracion.dto.ConfiguracionUpdateRequest;
import com.nutriapp.modules.configuracion.entity.ConfiguracionSistema;
import com.nutriapp.modules.configuracion.repository.ConfiguracionRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fuente de verdad de los parámetros de negocio (descuento/comisión), editables por el admin.
 * La fila singleton la crea la migración V004; {@link #cargar()} falla si no existe (no la fabrica
 * en runtime para no enmascarar un problema de migración). Lecturas puntuales contra la DB — el
 * consumo es infrecuente (una vez por emisión / por conversión), no hace falta cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository repository;

    @Transactional(readOnly = true)
    public ConfiguracionResponse get() {
        ConfiguracionSistema c = cargar();
        return new ConfiguracionResponse(c.getDescuentoPct(), c.getComisionPct());
    }

    /** % de descuento global (fijo) aplicado a toda receta emitida. */
    @Transactional(readOnly = true)
    public BigDecimal getDescuentoPct() {
        return cargar().getDescuentoPct();
    }

    /** % de comisión del nutricionista, aplicado al convertir una receta. */
    @Transactional(readOnly = true)
    public BigDecimal getComisionPct() {
        return cargar().getComisionPct();
    }

    @Transactional
    public ConfiguracionResponse actualizar(ConfiguracionUpdateRequest req) {
        ConfiguracionSistema c = cargar();
        c.setDescuentoPct(req.descuentoPct());
        c.setComisionPct(req.comisionPct());
        ConfiguracionSistema saved = repository.save(c);
        log.info("Configuración actualizada: descuento={}% comisión={}%",
                saved.getDescuentoPct(), saved.getComisionPct());
        return new ConfiguracionResponse(saved.getDescuentoPct(), saved.getComisionPct());
    }

    private ConfiguracionSistema cargar() {
        return repository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay fila de configuracion_sistema — falta el seed de la migración V004"));
    }
}
