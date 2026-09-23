package com.bonosapp.modules.registro;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Interruptor y no anotación: si tras el deploy queda cacheado el front viejo, se apaga sin redeploy. */
@ConfigurationProperties(prefix = "bonosapp.registro")
public record RegistroProperties(Boolean exigirDatosProfesionales) {

    public RegistroProperties {
        exigirDatosProfesionales = exigirDatosProfesionales == null || exigirDatosProfesionales;
    }
}
