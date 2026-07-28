package com.nutriapp.integrations;

/**
 * Lanzada por los adapters en modo stub (o ante un proveedor caído irrecuperable).
 * La lógica de negocio la captura y degrada: cupón queda PENDIENTE de sync,
 * notificación queda QUEUED. Nunca debe abortar la operación local del usuario.
 */
public class IntegrationUnavailableException extends RuntimeException {

    private final String proveedor;

    public IntegrationUnavailableException(String proveedor) {
        super("integración " + proveedor + " no conectada");
        this.proveedor = proveedor;
    }

    public String getProveedor() {
        return proveedor;
    }

    /**
     * Mensaje humano por proveedor para el 503 (tarea 2.7: "texto claro por proveedor").
     * El {@link #getMessage()} crudo sigue siendo el técnico para logs.
     */
    public String mensajeUsuario() {
        if (proveedor == null) {
            return "Una integración externa no está disponible en este momento.";
        }
        return switch (proveedor.toLowerCase()) {
            case "tiendanube" -> "La tienda (TiendaNube) no está disponible en este momento. "
                    + "Lo pendiente se reintenta automáticamente.";
            case "contabilium" -> "El ERP (Contabilium) no está conectado en este momento.";
            case "mail" -> "El servicio de email no está disponible en este momento.";
            case "whatsapp" -> "El servicio de WhatsApp no está disponible en este momento.";
            default -> "La integración " + proveedor + " no está disponible en este momento.";
        };
    }
}
