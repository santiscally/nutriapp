package com.nutriapp.modules.receta.service;

import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.receta.entity.EstadoReceta;
import com.nutriapp.modules.receta.entity.Receta;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Arma el link {@code wa.me} con el que la nutricionista le manda la receta al paciente desde su
 * propio WhatsApp (tarea 2.4 del plan, decisión del 2026-07-28).
 *
 * <p>Reemplaza al envío automático por Cloud API: sin WABA, sin número de empresa y sin template
 * aprobado por Meta, y el mensaje le llega al paciente desde el número que ya conoce. El costo es
 * que el envío deja de ser garantizable por el sistema — por eso WhatsApp ya no es un canal de la
 * cola de notificaciones ni una integración con estado: es un link, y el envío lo hace una persona.
 * El canal automático sigue siendo el email.
 */
@Component
public class WaMeLinkBuilder {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String BASE = "https://wa.me/";

    /**
     * Link listo para abrir, o {@code null} si no corresponde ofrecerlo.
     *
     * <p>Devuelve null cuando la receta ya no se puede usar (sólo PENDIENTE tiene un cupón vigente
     * que valga la pena mandar: una ANULADA o VENCIDA daría un código muerto) o cuando el paciente
     * no tiene un teléfono utilizable. El front decide mostrar el botón por la presencia del campo,
     * sin repetir la regla de estados.
     */
    public String forReceta(Receta receta, Paciente paciente) {
        if (receta == null || paciente == null || receta.getEstado() != EstadoReceta.PENDIENTE) {
            return null;
        }
        String telefono = soloDigitos(paciente.getWhatsapp());
        if (telefono == null) {
            return null;
        }
        return BASE + telefono + "?text=" + encode(mensaje(receta, paciente));
    }

    /** Mismo texto que usaba el template de la cola, ahora que el envío es manual. */
    private String mensaje(Receta receta, Paciente paciente) {
        return "Hola " + paciente.getNombre() + "! 🌱 Tu bono profesional con " + pct(receta.getDescuentoPct())
                + " de descuento ya está listo. Código: *" + receta.getCodigo() + "* "
                + "(válido hasta el " + FECHA.format(receta.getVenceAt()) + "). "
                + "Usalo al comprar en la tienda online.";
    }

    /**
     * wa.me quiere el número en E.164 sin {@code +} ni separadores. Los pacientes se validan en
     * E.164 desde el front, pero el dato puede venir de una carga vieja con espacios o guiones.
     */
    private String soloDigitos(String whatsapp) {
        if (whatsapp == null) {
            return null;
        }
        String digitos = whatsapp.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /**
     * {@code URLEncoder} es form-encoding: manda los espacios como {@code +}, que WhatsApp muestra
     * literal en algunos clientes. Se pasan a {@code %20}, que es lo que espera un query param.
     */
    private String encode(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String pct(BigDecimal descuento) {
        return descuento.stripTrailingZeros().toPlainString() + "%";
    }
}
