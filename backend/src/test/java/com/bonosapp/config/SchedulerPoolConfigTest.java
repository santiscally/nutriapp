package com.bonosapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * S-06 — el pool del scheduler no puede volver a quedar en un solo hilo.
 *
 * <p>Con el default de Spring (1 hilo) los seis {@code @Scheduled} comparten hilo: el processor de
 * webhooks, que es el que pasa un bono a APLICADO cada 20s, espera detrás del polling de TiendaNube
 * y del dispatcher de mails, que hacen I/O de red. El síntoma es un dashboard que tarda en
 * actualizarse, y no deja rastro en ningún log.
 *
 * <p>Ata el {@code application.yml} real contra la clase de Spring que lee la propiedad: si alguien
 * mueve la clave de lugar o la anida mal, el binding devuelve el default y esto falla. Un test que
 * mirara el YAML como texto no lo detectaría.
 */
class SchedulerPoolConfigTest {

    @Test
    void elSchedulerNoCorreConUnSoloHilo() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        fuentes.forEach(env.getPropertySources()::addLast);

        TaskSchedulingProperties props = Binder.get(env)
                .bind("spring.task.scheduling", TaskSchedulingProperties.class)
                .orElseGet(TaskSchedulingProperties::new);

        assertThat(props.getPool().getSize())
                .as("pool del scheduler en application.yml")
                .isGreaterThan(1);
    }
}
