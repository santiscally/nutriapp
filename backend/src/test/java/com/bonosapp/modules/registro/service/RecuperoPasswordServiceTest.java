package com.bonosapp.modules.registro.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.keycloak.KeycloakAdminClient;
import com.bonosapp.integrations.keycloak.KeycloakAdminException;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** S-09 — el endpoint público no puede delatar qué mails están registrados. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecuperoPasswordServiceTest {

    @Mock NutricionistaRepository repository;
    @Mock KeycloakAdminClient keycloak;

    @InjectMocks RecuperoPasswordService service;

    private Nutricionista cuenta(EstadoValidacion estado, boolean activo) {
        Nutricionista n = new Nutricionista();
        n.setEmail("ana@x.com");
        n.setKeycloakUserId("kc-123");
        n.setEstadoValidacion(estado);
        n.setActivo(activo);
        when(repository.findByEmailIgnoreCaseAndDeletedAtIsNull("ana@x.com")).thenReturn(Optional.of(n));
        return n;
    }

    @Test
    void cuentaAprobadaYActiva_recibeElMail() {
        cuenta(EstadoValidacion.APROBADA, true);

        service.enviarMail("ana@x.com");

        verify(keycloak).enviarMailDeReseteo(eq("kc-123"), anyInt());
    }

    @Test
    void emailQueNoExiste_noHaceNadaYNoFalla() {
        when(repository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service.enviarMail("nadie@x.com")).doesNotThrowAnyException();

        verify(keycloak, never()).enviarMailDeReseteo(anyString(), anyInt());
    }

    /** Cambiar la contraseña no la deja entrar: el mail sólo la haría creer que sí. */
    @Test
    void cuentaPendienteDeAprobacion_noRecibeMail() {
        cuenta(EstadoValidacion.PENDIENTE, false);

        service.enviarMail("ana@x.com");

        verify(keycloak, never()).enviarMailDeReseteo(anyString(), anyInt());
    }

    @Test
    void cuentaDesactivadaPorElAdmin_noRecibeMail() {
        cuenta(EstadoValidacion.APROBADA, false);

        service.enviarMail("ana@x.com");

        verify(keycloak, never()).enviarMailDeReseteo(anyString(), anyInt());
    }

    /**
     * Si Keycloak no lo pudo mandar (realm sin SMTP), el endpoint tiene que responder igual que
     * siempre: propagar el error distingue una cuenta que existe de una que no.
     */
    @Test
    void siKeycloakFalla_noSePropagaElError() {
        cuenta(EstadoValidacion.APROBADA, true);
        doThrow(new KeycloakAdminException("sin smtp", null))
                .when(keycloak).enviarMailDeReseteo(anyString(), anyInt());

        assertThatCode(() -> service.enviarMail("ana@x.com")).doesNotThrowAnyException();
    }
}
