package com.nutriapp.modules.notificacion.service;

import com.nutriapp.modules.paciente.entity.Paciente;
import com.nutriapp.modules.receta.entity.Receta;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Arma asunto/cuerpo de las notificaciones de receta. Texto plano por ahora (el HTML del mail se
 * define en Fase 2 con el proveedor real). El código de cupón y la vigencia son los datos
 * accionables.
 *
 * <p>El texto de WhatsApp se mudó a {@code WaMeLinkBuilder} cuando ese canal dejó de ser una
 * notificación automática y pasó a ser un link que abre la nutricionista (2.4).
 */
@Component
public class NotificacionTemplates {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String asuntoEmail(Receta receta) {
        return "Tu bono profesional " + receta.getCodigo() + " con descuento en TBC";
    }

    public String cuerpoEmail(Receta receta, Paciente paciente) {
        return "Hola " + paciente.getNombre() + ",\n\n"
                + "Tu nutricionista te emitió un bono profesional con un " + pct(receta.getDescuentoPct())
                + " de descuento.\n\n"
                + "Código de descuento: " + receta.getCodigo() + "\n"
                + "Válido hasta: " + FECHA.format(receta.getVenceAt()) + "\n\n"
                + "Usá el código al finalizar tu compra en la tienda online.\n\n"
                + "Saludos,\nNutriApp";
    }

    private String pct(BigDecimal descuento) {
        return descuento.stripTrailingZeros().toPlainString() + "%";
    }
}
