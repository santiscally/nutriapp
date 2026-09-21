package com.bonosapp.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.modules.admin.dto.AdminRecetaResponse;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.receta.dto.RecetaResponse;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.receta.service.RecetaService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** S-14 — la solapa BONOS del admin: de quién es cada bono y cuánto facturó la orden. */
@ExtendWith(MockitoExtension.class)
class AdminRecetaServiceTest {

    @Mock RecetaRepository repo;
    @Mock RecetaService recetaService;
    @Mock NutricionistaRepository nutricionistaRepository;

    private AdminRecetaService service() {
        return new AdminRecetaService(repo, recetaService, new ProfesionalesLookup(nutricionistaRepository));
    }

    private static Receta receta(UUID nutriId, BigDecimal ordenTotal) {
        Receta r = new Receta();
        r.setId(UUID.randomUUID());
        r.setCodigo("RX-ADMIN1");
        r.setEstado(EstadoReceta.APLICADA);
        r.setNutricionistaId(nutriId);
        r.setOrdenTotal(ordenTotal);
        return r;
    }

    private static RecetaResponse response(Receta r) {
        return new RecetaResponse(r.getId(), r.getCodigo(), r.getEstado().name(), null, List.of(),
                new BigDecimal("20.00"), Instant.now(), LocalDate.now(), "OK", null, null, null,
                new RecetaResponse.Conversion(306, Instant.now(), new BigDecimal("1.00"),
                        new BigDecimal("3150.00"), null));
    }

    @Test
    void agregaElProfesionalYElTotalDeLaOrden() {
        UUID nutriId = UUID.randomUUID();
        Receta r = receta(nutriId, new BigDecimal("315000.00"));
        Nutricionista n = new Nutricionista();
        n.setId(nutriId);
        n.setNombre("Ana");
        n.setApellido("García");
        n.setEmail("ana@x.com");
        when(repo.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(r)));
        when(nutricionistaRepository.findAllById(any())).thenReturn(List.of(n));
        when(recetaService.toResponse(r)).thenReturn(response(r));

        Page<AdminRecetaResponse> page = service()
                .search(null, null, null, null, null, PageRequest.of(0, 20));

        AdminRecetaResponse fila = page.getContent().get(0);
        assertThat(fila.nutricionista().nombre()).isEqualTo("Ana");
        assertThat(fila.nutricionista().email()).isEqualTo("ana@x.com");
        assertThat(fila.conversion().ordenTotal()).isEqualByComparingTo("315000.00");
        assertThat(fila.conversion().comisionMonto()).isEqualByComparingTo("3150.00");
        assertThat(fila.codigo()).isEqualTo("RX-ADMIN1");
    }

    /** El bono de una cuenta borrada tiene que seguir listándose, no romper la página. */
    @Test
    void bonoSinProfesionalVivoNoRompeElListado() {
        Receta r = receta(UUID.randomUUID(), null);
        when(repo.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(r)));
        when(nutricionistaRepository.findAllById(any())).thenReturn(List.of());
        when(recetaService.toResponse(r)).thenReturn(response(r));

        Page<AdminRecetaResponse> page = service()
                .search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).nutricionista()).isNull();
    }

    /** La ventana de fechas es inclusive en los dos extremos: el "hasta" es el día entero. */
    @Test
    void laVentanaDeFechasIncluyeElDiaHasta() {
        when(repo.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().search(null, null, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                PageRequest.of(0, 20));

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> hasta = ArgumentCaptor.forClass(Instant.class);
        verify(repo).search(isNull(), isNull(), isNull(), isNull(), desde.capture(), hasta.capture(),
                eq((Pageable) PageRequest.of(0, 20)));
        assertThat(desde.getValue()).isEqualTo(Instant.parse("2026-09-01T03:00:00Z"));
        assertThat(hasta.getValue()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
    }
}
