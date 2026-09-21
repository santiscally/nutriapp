package com.bonosapp.modules.admin.service;

import com.bonosapp.modules.admin.dto.AdminDashboardResumenResponse;
import com.bonosapp.modules.admin.dto.AdminEstadisticasResponse;
import com.bonosapp.modules.admin.dto.AdminRecetaResponse;
import com.bonosapp.modules.nutricionista.entity.EstadoValidacion;
import com.bonosapp.modules.nutricionista.repository.NutricionistaRepository;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import com.bonosapp.modules.receta.service.RecetaService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-13 — solapa PANEL del admin. Mismas métricas y misma ventana que el dashboard de la profesional
 * (reusa sus queries con el id en null, en vez de una definición propia que se desincronice), más el
 * facturado y el estado del padrón.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int MESES_DEFAULT = 6;
    private static final int MESES_MAX = 24;

    /** Sin filtro de profesional: es lo que distingue estas métricas de las del panel de cada una. */
    private static final UUID TODAS = null;

    private final RecetaRepository recetaRepository;
    private final RecetaService recetaService;
    private final NutricionistaRepository nutricionistaRepository;
    private final ProfesionalesLookup profesionales;

    @Transactional(readOnly = true)
    public AdminDashboardResumenResponse resumen() {
        LocalDate inicioMes = LocalDate.now(AR).withDayOfMonth(1);
        LocalDate inicioMesSiguiente = inicioMes.plusMonths(1);
        Instant desde = inicioMes.atStartOfDay(AR).toInstant();
        Instant hasta = inicioMesSiguiente.atStartOfDay(AR).toInstant();

        List<Receta> ultimas = recetaRepository.findTop8ByDeletedAtIsNullOrderByEmitidaAtDesc();
        Map<UUID, AdminRecetaResponse.Profesional> porId = profesionales.de(ultimas);

        return new AdminDashboardResumenResponse(
                recetaRepository.countPorEstado(TODAS, EstadoReceta.PENDIENTE),
                recetaRepository.countConvertidasEntre(TODAS, desde, hasta),
                recetaRepository.countVencidasEntre(TODAS, inicioMes, inicioMesSiguiente),
                recetaRepository.sumComisionEntre(TODAS, desde, hasta),
                recetaRepository.sumFacturadoEntre(TODAS, desde, hasta),
                nutricionistaRepository.countByActivoTrueAndDeletedAtIsNull(),
                nutricionistaRepository.countByEstadoValidacionAndDeletedAtIsNull(EstadoValidacion.PENDIENTE),
                ultimas.stream().map(r -> AdminRecetaResponse.de(
                        recetaService.toResponse(r),
                        porId.get(r.getNutricionistaId()),
                        r.getOrdenTotal())).toList());
    }

    @Transactional(readOnly = true)
    public AdminEstadisticasResponse estadisticas(int meses) {
        int n = Math.max(1, Math.min(MESES_MAX, meses <= 0 ? MESES_DEFAULT : meses));
        YearMonth actual = YearMonth.now(AR);

        List<AdminEstadisticasResponse.MesStat> serie = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = actual.minusMonths(i);
            Instant desde = ym.atDay(1).atStartOfDay(AR).toInstant();
            Instant hasta = ym.plusMonths(1).atDay(1).atStartOfDay(AR).toInstant();
            serie.add(new AdminEstadisticasResponse.MesStat(
                    ym.getYear(),
                    ym.getMonthValue(),
                    recetaRepository.countEmitidasEntre(TODAS, desde, hasta),
                    recetaRepository.countConvertidasEntre(TODAS, desde, hasta),
                    recetaRepository.sumComisionEntre(TODAS, desde, hasta),
                    recetaRepository.sumFacturadoEntre(TODAS, desde, hasta)));
        }
        return new AdminEstadisticasResponse(serie);
    }
}
