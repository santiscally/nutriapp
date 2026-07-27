package com.nutriapp.modules.dashboard.service;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.modules.dashboard.dto.CierreMensualResponse;
import com.nutriapp.modules.dashboard.dto.DashboardResumenResponse;
import com.nutriapp.modules.dashboard.dto.EstadisticasResponse;
import com.nutriapp.modules.nutricionista.service.NutricionistaService;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.receta.dto.RecetaResponse;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import com.nutriapp.modules.receta.service.RecetaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int ESTADISTICAS_MESES_DEFAULT = 6;
    private static final int ESTADISTICAS_MESES_MAX = 24;

    private final RecetaRepository recetaRepository;
    private final RecetaService recetaService;
    private final NutricionistaService nutricionistaService;
    private final PacienteRepository pacienteRepository;

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

    /** Cierre mensual: tasa de conversión, ventas y comisión del mes + detalle de convertidas. */
    @Transactional(readOnly = true)
    public CierreMensualResponse cierreMensual(int year, int month) {
        UUID nutriId = nutricionistaService.getCurrent().getId();

        YearMonth ym;
        try {
            ym = YearMonth.of(year, month);
        } catch (DateTimeException ex) {
            throw new ConflictException("Período inválido: mes debe estar entre 1 y 12");
        }
        Instant desde = ym.atDay(1).atStartOfDay(AR).toInstant();
        Instant hasta = ym.plusMonths(1).atDay(1).atStartOfDay(AR).toInstant();

        long emitidas = recetaRepository.countEmitidasEntre(nutriId, desde, hasta);
        long aplicadas = recetaRepository.countAplicadasEntre(nutriId, EstadoReceta.APLICADA, desde, hasta);
        BigDecimal ventas = recetaRepository.sumVentasEntre(nutriId, desde, hasta);
        BigDecimal comision = recetaRepository.sumComisionEntre(nutriId, desde, hasta);
        BigDecimal tasa = emitidas == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(aplicadas).divide(BigDecimal.valueOf(emitidas), 2, RoundingMode.HALF_UP);

        List<Receta> convertidas = recetaRepository.findAplicadasEntre(nutriId, desde, hasta);
        Map<UUID, Paciente> pacientes = new LinkedHashMap<>();
        List<CierreMensualResponse.Detalle> detalle = convertidas.stream()
                .map(r -> {
                    Paciente p = pacientes.computeIfAbsent(r.getPacienteId(),
                            pid -> pacienteRepository.findById(pid).orElse(null));
                    String nombre = p != null ? (p.getNombre() + " " + p.getApellido()) : "—";
                    return new CierreMensualResponse.Detalle(
                            r.getCodigo(), nombre, r.getOrdenTotal(), r.getComisionMonto(), r.getOrdenPaidAt());
                })
                .toList();

        return new CierreMensualResponse(
                year, month, emitidas, aplicadas, tasa, ventas, comision, detalle);
    }

    /**
     * Serie mensual (últimos {@code meses}, cronológica) para los gráficos del dashboard: recetas
     * emitidas/aplicadas y ventas/comisión por mes. Reusa las mismas queries por ventana del cierre.
     * {@code meses} se acota a [1, 24]; default 6.
     */
    @Transactional(readOnly = true)
    public EstadisticasResponse estadisticas(int meses) {
        UUID nutriId = nutricionistaService.getCurrent().getId();
        int n = Math.max(1, Math.min(ESTADISTICAS_MESES_MAX,
                meses <= 0 ? ESTADISTICAS_MESES_DEFAULT : meses));
        YearMonth actual = YearMonth.now(AR);

        List<EstadisticasResponse.MesStat> serie = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = actual.minusMonths(i);
            Instant desde = ym.atDay(1).atStartOfDay(AR).toInstant();
            Instant hasta = ym.plusMonths(1).atDay(1).atStartOfDay(AR).toInstant();
            serie.add(new EstadisticasResponse.MesStat(
                    ym.getYear(),
                    ym.getMonthValue(),
                    recetaRepository.countEmitidasEntre(nutriId, desde, hasta),
                    recetaRepository.countAplicadasEntre(nutriId, EstadoReceta.APLICADA, desde, hasta),
                    recetaRepository.sumVentasEntre(nutriId, desde, hasta),
                    recetaRepository.sumComisionEntre(nutriId, desde, hasta)));
        }
        return new EstadisticasResponse(serie);
    }
}
