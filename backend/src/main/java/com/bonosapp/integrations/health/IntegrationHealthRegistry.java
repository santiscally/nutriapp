package com.bonosapp.integrations.health;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registro en memoria del último resultado de cada integración externa, para dar visibilidad
 * al admin (tarea 2.7: {@code GET /admin/integraciones/estado}). Cada componente que habla con
 * un proveedor registra éxito o error en su punto de interacción (cupón sync, sync de productos,
 * dispatcher de notificaciones). Es efímero (se resetea al reiniciar): sirve para "¿respondió
 * recién?" / "¿cuál fue el último error?" — el estado durable (cupones pendientes, notifs QUEUED,
 * catálogo sincronizado) sale de la DB, no de acá.
 */
@Component
public class IntegrationHealthRegistry {

    /** Proveedores externos observados. (WhatsApp no está: es un link manual, no una API — 2.4.) */
    public enum Proveedor {
        CONTABILIUM, TIENDANUBE, MAIL
    }

    /** Foto del último éxito/error de un proveedor. Todos los campos pueden ser null (nunca interactuó). */
    public record Health(Instant ultimoExitoAt, Instant ultimoErrorAt, String ultimoError) {
        static final Health VACIO = new Health(null, null, null);

        /**
         * Disponibilidad inferida del último resultado: true si el último éxito es más nuevo que el
         * último error, false si el último error es más nuevo, null si nunca hubo interacción.
         */
        public Boolean disponible() {
            if (ultimoExitoAt == null && ultimoErrorAt == null) {
                return null;
            }
            if (ultimoErrorAt == null) {
                return Boolean.TRUE;
            }
            if (ultimoExitoAt == null) {
                return Boolean.FALSE;
            }
            return ultimoExitoAt.isAfter(ultimoErrorAt);
        }
    }

    private final Map<Proveedor, Health> estado = new EnumMap<>(Proveedor.class);

    public synchronized void registrarExito(Proveedor proveedor) {
        Health actual = estado.getOrDefault(proveedor, Health.VACIO);
        estado.put(proveedor, new Health(Instant.now(), actual.ultimoErrorAt(), actual.ultimoError()));
    }

    public synchronized void registrarError(Proveedor proveedor, String mensaje) {
        Health actual = estado.getOrDefault(proveedor, Health.VACIO);
        estado.put(proveedor, new Health(actual.ultimoExitoAt(), Instant.now(), mensaje));
    }

    public synchronized Health get(Proveedor proveedor) {
        return estado.getOrDefault(proveedor, Health.VACIO);
    }
}
