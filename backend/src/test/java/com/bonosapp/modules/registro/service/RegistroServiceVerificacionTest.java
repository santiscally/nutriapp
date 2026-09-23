package com.bonosapp.modules.registro.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.keycloak.KeycloakAdminClient;
import com.bonosapp.integrations.keycloak.KeycloakAdminException;
import com.bonosapp.modules.notificacion.service.NotificacionService;
import com.bonosapp.modules.nutricionista.NutricionistaProperties;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.nutricionista.service.ArchivoService;
import com.bonosapp.modules.profesion.service.ProfesionService;
import com.bonosapp.modules.registro.dto.RegistroRequest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** S-10 — el mail de "validá tu mail" al registrarse, y su reenvío. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistroServiceVerificacionTest {

    @Mock NutricionistaRepository repository;
    @Mock KeycloakAdminClient keycloak;
    @Mock ArchivoService archivoService;
    @Mock NotificacionService notificaciones;
    @Mock ProfesionService profesiones;

    private RegistroService service() {
        NutricionistaProperties props =
                new NutricionistaProperties(new BigDecimal("15.00"), new BigDecimal("1.00"));
        return new RegistroService(repository, keycloak, archivoService, props, notificaciones, profesiones,
                new com.bonosapp.modules.registro.RegistroProperties(true));
    }

    private RegistroRequest alta() {
        return new RegistroRequest("Ana", "García", "ana@x.com", "+5491155551234", "1234",
                "Buenos Aires", "Nutricionista", "30123456", "27301234564", "Monotributo", "secreto123");
    }

    private void keycloakCrea() {
        when(repository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(repository.findByDniAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(keycloak.registrarNutricionista(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-nuevo");
        when(repository.save(any(Nutricionista.class))).thenAnswer(inv -> {
            Nutricionista n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
    }

    @Test
    void alRegistrarse_seMandaElMailDeVerificacion() {
        keycloakCrea();

        service().registrar(alta(), null);

        verify(keycloak).enviarMailDeVerificacion(eq("kc-nuevo"), anyInt());
    }

    /**
     * El alta tiene que quedar hecha aunque el mail no salga: si no, un proveedor caído deja a la
     * persona sin solicitud Y sin aviso, y el admin nunca se entera de que quiso registrarse.
     */
    @Test
    void siElMailDeVerificacionFalla_elRegistroIgualQueda() {
        keycloakCrea();
        doThrow(new KeycloakAdminException("sin smtp", null))
                .when(keycloak).enviarMailDeVerificacion(anyString(), anyInt());

        assertThatCode(() -> service().registrar(alta(), null)).doesNotThrowAnyException();

        verify(repository).save(any(Nutricionista.class));
        verify(keycloak, never()).deleteUser(anyString());
    }

    /** S-11: con el interruptor prendido faltan datos → 422 con TODOS los faltantes, y sin tocar Keycloak. */
    @Test
    void sinDatosProfesionales_rechazaConTodosLosFaltantes() {
        keycloakCrea();
        RegistroRequest incompleto = new RegistroRequest("Ana", "García", "ana@x.com", "+5491155551234",
                "MN 1234", null, "  ", "30123456", "27301234564", "Monotributo", "secreto123");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().registrar(incompleto, null))
                .isInstanceOf(com.bonosapp.common.error.UnprocessableException.class)
                .hasMessageContaining("profesión")
                .hasMessageContaining("jurisdicción")
                .hasMessageContaining("sólo con dígitos");
        verify(keycloak, never()).registrarNutricionista(anyString(), anyString(), anyString(), anyString());
    }

    /** El interruptor apagado es la salida si el front viejo sigue cacheado tras el deploy. */
    @Test
    void conElInterruptorApagado_aceptaAltasSinDatosProfesionales() {
        keycloakCrea();
        RegistroService sinExigir = new RegistroService(repository, keycloak, archivoService,
                new NutricionistaProperties(new BigDecimal("15.00"), new BigDecimal("1.00")),
                notificaciones, profesiones, new com.bonosapp.modules.registro.RegistroProperties(false));
        RegistroRequest viejo = new RegistroRequest("Ana", "García", "ana@x.com", "+5491155551234",
                "CABA · N° 1234", null, null, "30123456", "27301234564", "Monotributo", "secreto123");

        assertThatCode(() -> sinExigir.registrar(viejo, null)).doesNotThrowAnyException();
    }

    @Test
    void reenvio_deUnEmailQueNoExiste_noHaceNada() {
        when(repository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service().reenviarVerificacion("nadie@x.com")).doesNotThrowAnyException();

        verify(keycloak, never()).enviarMailDeVerificacion(anyString(), anyInt());
    }

    @Test
    void reenvio_deUnaCuentaExistente_mandaElMail() {
        Nutricionista n = new Nutricionista();
        n.setEmail("ana@x.com");
        n.setKeycloakUserId("kc-123");
        when(repository.findByEmailIgnoreCaseAndDeletedAtIsNull("ana@x.com")).thenReturn(Optional.of(n));

        service().reenviarVerificacion("ana@x.com");

        verify(keycloak).enviarMailDeVerificacion(eq("kc-123"), anyInt());
    }
}
