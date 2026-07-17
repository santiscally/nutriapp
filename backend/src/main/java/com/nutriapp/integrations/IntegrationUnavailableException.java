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
}
