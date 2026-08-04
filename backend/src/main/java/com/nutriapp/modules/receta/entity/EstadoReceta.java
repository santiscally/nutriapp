package com.nutriapp.modules.receta.entity;

/**
 * Ciclo de vida de la receta: {@code PENDIENTE → APLICADA → LIQUIDADA}, con {@code VENCIDA} y
 * {@code ANULADA} como salidas desde PENDIENTE.
 */
public enum EstadoReceta {
    /** Emitida, con cupón vigente, todavía sin comprar. */
    PENDIENTE,
    /** El paciente compró con el cupón en TiendaNube (conversión). Genera comisión. */
    APLICADA,
    /** Se cumplió la vigencia sin que el paciente comprara. */
    VENCIDA,
    /** La nutricionista la dio de baja antes de que se usara. */
    ANULADA,
    /**
     * C-05 (post-demo 2026-07-31): el admin ya le pagó a la nutricionista la comisión de esta
     * receta. Estado terminal — sigue contando como convertida en los cierres históricos, pero
     * no vuelve a aparecer entre las pendientes de liquidar.
     */
    LIQUIDADA;

    /** Estados en los que la receta ya convirtió (el paciente compró). Base de todo cierre. */
    public boolean esConvertida() {
        return this == APLICADA || this == LIQUIDADA;
    }
}
