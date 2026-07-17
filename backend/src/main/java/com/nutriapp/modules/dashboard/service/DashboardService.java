package com.nutriapp.modules.dashboard.service;

import com.nutriapp.modules.dashboard.dto.DashboardResumenResponse;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import com.nutriapp.modules.receta.dto.RecetaResponse;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import com.nutriapp.modules.receta.service.RecetaService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final RecetaRepository recetaRepository;
    private final RecetaService recetaService;
    private final NutricionistaService nutricionistaService;

    @Transactional(readOnly = true)
    public DashboardResumenResponse resumen() {
        UUID nutriId = nutricionistaService.getCurrent().getId();

        LocalDate hoy = LocalDate.now(AR);
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        LocalDate inicioMesSiguiente = inicioMes.plusMonths(1);
        Instant desde = inicioMes.atStartOfDay(AR).toInstant();
        Instant hasta = inicioMesSiguiente.atStartOfDay(AR).toInstant();

        long pendientes = recetaRepository
                .countByNutricionistaIdAndEstadoAndDeletedAtIsNull(nutriId, EstadoReceta.PENDIENTE);
        long aplicadasMes = recetaRepository
                .countAplicadasEntre(nutriId, EstadoReceta.APLICADA, desde, hasta);
        long vencidasMes = recetaRepository
                .countVencidasEntre(nutriId, inicioMes, inicioMesSiguiente);

        List<RecetaResponse> ultimas = recetaRepository
                .findTop8ByNutricionistaIdAndDeletedAtIsNullOrderByEmitidaAtDesc(nutriId)
                .stream().map(recetaService::toResponse).toList();

        return new DashboardResumenResponse(
                pendientes,
                aplicadasMes,
                vencidasMes,
                recetaRepository.sumComisionEntre(nutriId, desde, hasta),
                recetaRepository.sumVentasEntre(nutriId, desde, hasta),
                ultimas);
    }
}
