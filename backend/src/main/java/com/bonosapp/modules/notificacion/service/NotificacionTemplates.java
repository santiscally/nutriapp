package com.bonosapp.modules.notificacion.service;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.modules.notificacion.NotificacionProperties;
import com.bonosapp.modules.nutricionista.entity.Nutricionista;
import com.bonosapp.modules.paciente.entity.Paciente;
import com.bonosapp.modules.receta.entity.Receta;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Arma asunto/cuerpo de las notificaciones: el bono profesional que va al paciente y los avisos
 * del alta pública de una nutricionista. Texto plano por ahora (el HTML del mail se define en
 * Fase 2 con el proveedor real).
 *
 * <p>El texto de WhatsApp se mudó a {@code WaMeLinkBuilder} cuando ese canal dejó de ser una
 * notificación automática y pasó a ser un link que abre la nutricionista (2.4).
 */
@Component
@RequiredArgsConstructor
public class NotificacionTemplates {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FIRMA = "\n\nSaludos,\nBonosApp";

    private final NotificacionProperties props;
    private final IntegrationsProperties integrations;
    private final BonoContenido bono;

    /**
     * F-22 — asunto sin gancho comercial: identifica el mail por el código y no trae la palabra
     * "descuento", que es de las que empujan el mail a la pestaña Promociones.
     */
    public String asuntoEmail(Receta receta) {
        return "Tu bono profesional " + receta.getCodigo();
    }

    /**
     * F-18 / F-19 — el cuerpo dice DE QUÉ es el bono y trae el link de cupón, que lo aplica solo.
     * El código queda igual, por si la paciente prefiere tipearlo en el checkout.
     */
    public String cuerpoEmail(Receta receta, Paciente paciente) {
        String descripcion = bono.descripcionProductos(receta);
        return "Hola " + paciente.getNombre() + ",\n\n"
                + "Tu bono profesional" + (descripcion == null ? "" : " de " + descripcion)
                + " con " + pct(receta.getDescuentoPct()) + " de descuento ya está listo.\n\n"
                + "Código: " + receta.getCodigo() + "\n"
                + "Válido hasta: " + FECHA.format(receta.getVenceAt()) + "\n\n"
                + comoUsarlo(receta)
                + FIRMA;
    }

    /**
     * El link de cupón cuando hay tienda configurada; si no, la instrucción de tipear el código
     * (el mail tiene que seguir sirviendo con {@code TIENDANUBE_STORE_URL} vacío).
     */
    private String comoUsarlo(Receta receta) {
        String link = bono.linkCupon(receta);
        if (link == null) {
            return "Usá el código al finalizar tu compra en la tienda online." + tiendaLink();
        }
        return BonoContenido.INSTRUCCION_LINK + ":\n" + link;
    }

    public String asuntoRegistroRecibido() {
        return "Recibimos tu solicitud de registro en BonosApp";
    }

    public String cuerpoRegistroRecibido(Nutricionista n) {
        return "Hola " + n.getNombre() + ",\n\n"
                + "Recibimos tu solicitud para usar BonosApp y la estamos revisando.\n\n"
                + "Cuando la aprobemos te avisamos por este mismo mail y vas a poder ingresar con "
                + n.getEmail() + " y la contraseña que elegiste. Hasta entonces la cuenta todavía "
                + "no tiene acceso.\n\n"
                + "No hace falta que hagas nada más."
                + FIRMA;
    }

    public String asuntoRegistroAprobado() {
        return "Tu cuenta de BonosApp ya está activa";
    }

    public String cuerpoRegistroAprobado(Nutricionista n) {
        return "Hola " + n.getNombre() + ",\n\n"
                + "Aprobamos tu registro: ya podés entrar a BonosApp con " + n.getEmail()
                + " y la contraseña que elegiste al registrarte.\n\n"
                + "Ingresá acá: " + link("/ingresar") + "\n\n"
                + "Desde ahí cargás tus pacientes y emitís bonos profesionales con descuento."
                + FIRMA;
    }

    public String asuntoRegistroRechazado() {
        return "Sobre tu solicitud de registro en BonosApp";
    }

    public String cuerpoRegistroRechazado(Nutricionista n, String motivo) {
        String cuerpo = "Hola " + n.getNombre() + ",\n\n"
                + "Revisamos tu solicitud para usar BonosApp y por ahora no podemos aprobarla.\n";
        if (motivo != null && !motivo.isBlank()) {
            cuerpo += "\nMotivo: " + motivo.trim() + "\n";
        }
        return cuerpo + "\nSi creés que es un error, respondé este mail y lo revisamos." + FIRMA;
    }

    public String asuntoAdminNuevaSolicitud(Nutricionista n) {
        return "Nueva solicitud de registro: " + n.getNombre() + " " + n.getApellido();
    }

    public String cuerpoAdminNuevaSolicitud(Nutricionista n) {
        return "Hay una solicitud nueva esperando aprobación.\n\n"
                + "Nombre: " + n.getNombre() + " " + n.getApellido() + "\n"
                + "Email: " + n.getEmail() + "\n"
                + "Matrícula: " + orGuion(n.getMatricula()) + "\n"
                + "DNI: " + orGuion(n.getDni()) + "\n"
                + "Teléfono: " + orGuion(n.getTelefono()) + "\n\n"
                + "Aprobala o rechazala en " + link("/profesionales")
                + FIRMA;
    }

    /** Vacío si no hay tienda configurada: el mail sale igual, sin un link roto. */
    private String tiendaLink() {
        String tienda = integrations.tiendanube().storeUrlNormalizada();
        return tienda == null ? "" : "\n" + tienda;
    }

    private String link(String ruta) {
        return props.appUrlNormalizada() + ruta;
    }

    private String orGuion(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }

    private String pct(BigDecimal descuento) {
        return descuento.stripTrailingZeros().toPlainString() + "%";
    }
}
