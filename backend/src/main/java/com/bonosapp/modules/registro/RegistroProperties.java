package com.bonosapp.modules.registro;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param exigirDatosProfesionales profesión, jurisdicción y matrícula numérica obligatorias (S-11).
 *        Es un interruptor y no una anotación para poder apagarlo sin redeploy si, justo después
 *        de desplegar, alguien todavía tiene cacheado el front viejo que no los manda.
 */
@ConfigurationProperties(prefix = "bonosapp.registro")
public record RegistroProperties(Boolean exigirDatosProfesionales) {

    public RegistroProperties {
        exigirDatosProfesionales = exigirDatosProfesionales == null || exigirDatosProfesionales;
    }
}
