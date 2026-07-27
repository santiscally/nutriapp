package com.nutriapp.integrations.contabilium;

import com.nutriapp.integrations.IntegrationUnavailableException;
import lombok.extern.slf4j.Slf4j;

/**
 * Modo stub: no hay conexión con el ERP. Toda llamada degrada con
 * {@link IntegrationUnavailableException} — la lógica de negocio la captura y no pisa el catálogo
 * seedeado (los productos ya viven en la DB y se usan desde ahí).
 */
@Slf4j
public class StubContabiliumClient implements ContabiliumClient {

    @Override
    public CompanyInfo obtenerInfo() {
        log.info("[stub-contabilium] obtenerInfo — sin conexión");
        throw new IntegrationUnavailableException("contabilium");
    }

    @Override
    public ConceptoPage buscarConceptos(String filtro, int page) {
        log.info("[stub-contabilium] buscarConceptos filtro={} page={} — sin conexión", filtro, page);
        throw new IntegrationUnavailableException("contabilium");
    }
}
