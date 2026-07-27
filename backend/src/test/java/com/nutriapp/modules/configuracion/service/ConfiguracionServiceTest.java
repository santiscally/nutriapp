package com.nutriapp.modules.configuracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.modules.configuracion.dto.ConfiguracionUpdateRequest;
import com.nutriapp.modules.configuracion.entity.ConfiguracionSistema;
import com.nutriapp.modules.configuracion.repository.ConfiguracionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfiguracionServiceTest {

    @Mock ConfiguracionRepository repository;

    @InjectMocks ConfiguracionService service;

    private ConfiguracionSistema row(String descuento, String comision) {
        ConfiguracionSistema c = new ConfiguracionSistema();
        c.setId(UUID.randomUUID());
        c.setDescuentoPct(new BigDecimal(descuento));
        c.setComisionPct(new BigDecimal(comision));
        return c;
    }

    @Test
    void get_devuelveLosValoresVigentes() {
        when(repository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Optional.of(row("15", "10")));

        var r = service.get();

        assertThat(r.descuentoPct()).isEqualByComparingTo("15");
        assertThat(r.comisionPct()).isEqualByComparingTo("10");
    }

    @Test
    void actualizar_persisteYDevuelveLosNuevosValores() {
        ConfiguracionSistema c = row("15", "10");
        when(repository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(Optional.of(c));
        when(repository.save(any(ConfiguracionSistema.class))).thenAnswer(inv -> inv.getArgument(0));

        var r = service.actualizar(new ConfiguracionUpdateRequest(new BigDecimal("25"), new BigDecimal("12")));

        assertThat(c.getDescuentoPct()).isEqualByComparingTo("25");
        assertThat(c.getComisionPct()).isEqualByComparingTo("12");
        assertThat(r.descuentoPct()).isEqualByComparingTo("25");
        assertThat(r.comisionPct()).isEqualByComparingTo("12");
        verify(repository).save(c);
    }

    @Test
    void sinFilaDeConfig_lanzaIllegalState() {
        when(repository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDescuentoPct()).isInstanceOf(IllegalStateException.class);
    }
}
