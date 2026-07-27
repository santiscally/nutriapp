package com.nutriapp.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de los tests de integración: levanta la app real contra una Postgres de Testcontainers
 * (misma imagen que dev/prod → corren las migraciones Flyway reales, incluidos los seeds de
 * productos V003 y de configuración V004). Perfil {@code test} (no {@code dev}): el DevDataSeeder
 * NO corre, cada test siembra sus propios datos.
 *
 * <p>Requiere un daemon Docker. Los concretos se nombran {@code *IT} y corren en la fase
 * {@code verify} (failsafe), no en {@code mvn test}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresITBase {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
