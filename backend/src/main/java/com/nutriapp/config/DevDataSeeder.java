package com.nutriapp.config;

import com.nutriapp.modules.nutricionista.entity.EstadoValidacion;
import com.nutriapp.modules.nutricionista.entity.Nutricionista;
import com.nutriapp.modules.nutricionista.repository.NutricionistaRepository;
import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.paciente.repository.PacienteRepository;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import com.nutriapp.modules.receta.entity.RecetaItem;
import com.nutriapp.modules.receta.repository.RecetaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed de datos de desarrollo (idempotente, SOLO perfil dev — NUNCA prod).
 * Un único nutricionista de demo (linkea por email al usuario nutri@nutriapp.dev del realm)
 * con pacientes y recetas en estados variados, para que Fran tenga dashboard con data
 * desde el día uno. El catálogo de productos lo siembra Flyway (V003).
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String DEMO_EMAIL = "nutri@nutriapp.dev";
    private static final String ADMIN_EMAIL = "admin@nutriapp.dev";

    private final NutricionistaRepository nutricionistaRepository;
    private final PacienteRepository pacienteRepository;
    private final ProductoRepository productoRepository;
    private final RecetaRepository recetaRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // El admin también opera como nutricionista (acceso completo a la app): perfil APROBADO,
        // sin pacientes/recetas demo (arranca su propio espacio). Idempotente e independiente del
        // guard del demo de abajo → se crea también en una DB ya seedeada al reiniciar el backend.
        ensureNutriAprobado(ADMIN_EMAIL, "Admin", "BonosApp", "+5491100000000", "MN 00000");

        if (nutricionistaRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(DEMO_EMAIL).isPresent()) {
            log.info("[seed] nutricionista demo ya existe, skip");
            return;
        }

        Nutricionista nutri = new Nutricionista();
        nutri.setNombre("Nutri");
        nutri.setApellido("Demo");
        nutri.setEmail(DEMO_EMAIL);
        nutri.setTelefono("+5491133334444");
        nutri.setMatricula("MN 12345");
        nutri.setEstadoValidacion(EstadoValidacion.APROBADA);
        nutri.setValidadoAt(Instant.now());
        // V011: obligatorios. En el seed van los mismos valores que traía el global.
        nutri.setDescuentoPct(new BigDecimal("15.00"));
        nutri.setComisionPct(new BigDecimal("10.00"));
        // keycloakUserId se linkea en el primer login (NutricionistaService.findCurrent).
        nutri = nutricionistaRepository.save(nutri);

        Paciente juan = paciente(nutri, "Juan", "Pérez", "juan.perez@example.com", "+5491144443333");
        Paciente maria = paciente(nutri, "María", "González", "maria.gonzalez@example.com", "+5491155552222");
        Paciente lucia = paciente(nutri, "Lucía", "Fernández", "lucia.fernandez@example.com", "+5491166661111");
        Paciente pablo = paciente(nutri, "Pablo", "Ramírez", "pablo.ramirez@example.com", "+5491177770000");

        List<Producto> productos = productoRepository.findAll();
        if (productos.isEmpty()) {
            log.warn("[seed] no hay productos (¿corrió V003?), no se siembran recetas");
            return;
        }
        Producto whey = productos.stream().filter(p -> "WHEY-CHOC-1KG".equals(p.getSku())).findFirst().orElse(productos.get(0));
        Producto crea = productos.stream().filter(p -> "CREA-MONO-300".equals(p.getSku())).findFirst().orElse(productos.get(0));
        Producto omega = productos.stream().filter(p -> "OMEGA3-60".equals(p.getSku())).findFirst().orElse(productos.get(0));
        Producto colag = productos.stream().filter(p -> "COLAG-HIDRO-30".equals(p.getSku())).findFirst().orElse(productos.get(0));

        // 2 PENDIENTES, 2 APLICADAS (este mes), 1 VENCIDA, 1 ANULADA.
        pendiente(nutri, juan, whey, "RX-DEMO01", "1 medida post-entreno");
        pendiente(nutri, maria, crea, "RX-DEMO02", "5g diarios");
        aplicada(nutri, juan, omega, "RX-DEMO03", 12);
        aplicada(nutri, lucia, whey, "RX-DEMO04", 5);
        vencida(nutri, pablo, colag, "RX-DEMO05");
        anulada(nutri, maria, omega, "RX-DEMO06");

        log.info("[seed] nutricionista demo + 4 pacientes + 6 recetas creados");
    }

    /** Crea (si no existe) un nutricionista APROBADO para el email dado. Idempotente. */
    private Nutricionista ensureNutriAprobado(String email, String nombre, String apellido,
                                              String telefono, String matricula) {
        return nutricionistaRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseGet(() -> {
            Nutricionista n = new Nutricionista();
            n.setNombre(nombre);
            n.setApellido(apellido);
            n.setEmail(email);
            n.setTelefono(telefono);
            n.setMatricula(matricula);
            n.setEstadoValidacion(EstadoValidacion.APROBADA);
            n.setValidadoAt(Instant.now());
            // V011: obligatorios. En el seed van los mismos valores que traía el global.
            n.setDescuentoPct(new BigDecimal("15.00"));
            n.setComisionPct(new BigDecimal("10.00"));
            log.info("[seed] perfil nutricionista APROBADO creado para {}", email);
            return nutricionistaRepository.save(n);
        });
    }

    private Paciente paciente(Nutricionista nutri, String nombre, String apellido, String email, String wa) {
        Paciente p = new Paciente();
        p.setNutricionistaId(nutri.getId());
        p.setNombre(nombre);
        p.setApellido(apellido);
        p.setEmail(email);
        p.setWhatsapp(wa);
        return pacienteRepository.save(p);
    }

    private Receta baseReceta(Nutricionista nutri, Paciente paciente, Producto producto,
                              String codigo, String indicaciones, Instant emitidaAt) {
        Receta r = new Receta();
        r.setCodigo(codigo);
        r.setNutricionistaId(nutri.getId());
        r.setPacienteId(paciente.getId());
        r.setDescuentoPct(new BigDecimal("15.00"));
        r.setEmitidaAt(emitidaAt);
        r.setVenceAt(emitidaAt.atZone(AR).toLocalDate().plusDays(30));

        RecetaItem item = new RecetaItem();
        item.setProductoId(producto.getId());
        item.setCantidad(1);
        item.setPrecioLista(producto.getPrecio());
        item.setIndicaciones(indicaciones);
        r.addItem(item);
        return r;
    }

    private void pendiente(Nutricionista nutri, Paciente paciente, Producto producto, String codigo, String ind) {
        Receta r = baseReceta(nutri, paciente, producto, codigo, ind, Instant.now().minus(3, ChronoUnit.DAYS));
        r.setEstado(EstadoReceta.PENDIENTE);
        recetaRepository.save(r);
    }

    private void aplicada(Nutricionista nutri, Paciente paciente, Producto producto, String codigo, int diasAtras) {
        Instant emitida = Instant.now().minus(diasAtras, ChronoUnit.DAYS);
        Receta r = baseReceta(nutri, paciente, producto, codigo, "seguir indicaciones", emitida);
        r.setEstado(EstadoReceta.APLICADA);
        Instant paid = emitida.plus(2, ChronoUnit.DAYS);
        r.setAplicadaAt(paid);
        BigDecimal total = producto.getPrecio()
                .multiply(new BigDecimal("0.85"))  // 15% de descuento aplicado
                .setScale(2, RoundingMode.HALF_UP);
        r.setOrdenTiendanubeId(900000L + codigo.hashCode() % 1000);
        r.setOrdenNumero(100 + Math.abs(codigo.hashCode() % 900));
        r.setOrdenTotal(total);
        r.setOrdenPaidAt(paid);
        r.setComisionPct(new BigDecimal("10.00"));
        r.setComisionMonto(total.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP));
        recetaRepository.save(r);
    }

    private void vencida(Nutricionista nutri, Paciente paciente, Producto producto, String codigo) {
        Receta r = baseReceta(nutri, paciente, producto, codigo, "seguir indicaciones", Instant.now().minus(40, ChronoUnit.DAYS));
        r.setEstado(EstadoReceta.VENCIDA);
        r.setVenceAt(LocalDate.now(AR).minusDays(10));
        recetaRepository.save(r);
    }

    private void anulada(Nutricionista nutri, Paciente paciente, Producto producto, String codigo) {
        Receta r = baseReceta(nutri, paciente, producto, codigo, "seguir indicaciones", Instant.now().minus(5, ChronoUnit.DAYS));
        r.setEstado(EstadoReceta.ANULADA);
        r.setAnuladaAt(Instant.now().minus(4, ChronoUnit.DAYS));
        recetaRepository.save(r);
    }
}
