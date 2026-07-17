package com.nutriapp.integrations.contabilium;

import com.nutriapp.integrations.IntegrationUnavailableException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StubContabiliumClient implements ContabiliumClient {

    @Override
    public CompanyInfo obtenerInfo() {
        log.info("[stub-contabilium] obtenerInfo — sin conexión");
        throw new IntegrationUnavailableException("contabilium");
    }
}
