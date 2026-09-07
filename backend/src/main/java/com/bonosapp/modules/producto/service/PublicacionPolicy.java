package com.bonosapp.modules.producto.service;

import com.bonosapp.modules.producto.CatalogoProperties;
import com.bonosapp.modules.producto.entity.Producto;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Única definición de "¿este producto se puede recetar?". La consumen los dos procesos que escriben
 * el catálogo — el sync de Contabilium y el importador del maestro — y por eso vive acá y no adentro
 * de uno de ellos: si cada uno tuviera su copia, importar podría publicar algo que el sync bloqueó.
 *
 * <p>Las cinco reglas y de dónde salen (detalle en 07-maestro-articulos-y-catalogo.md §3.2):
 * <ol>
 *   <li><b>Precio ≥ mínimo</b> — tienen ~1000 artículos cargados a $1 que no son vendibles (call 44:52).</li>
 *   <li><b>Activo en el ERP</b> — C-14: "que pueda recetar todo, si está activo en Contabilium" (49:42).</li>
 *   <li><b>Tipo permitido</b> — mail de Gon 2026-08-03: "solo quedarnos con Producto". Ojo: en la
 *       cuenta de TBC ese campo también vale {@code Combo} (209 artículos), así que la regla tal cual
 *       la pidió deja afuera los packs — pendiente de confirmar, se cambia por env var.</li>
 *   <li><b>Rubro permitido</b> — C-13: solo producto terminado (144331), configurable.</li>
 *   <li><b>No bloqueado en el maestro</b> — "los bloqueados no deberían mostrarse en Nutriapp".</li>
 *   <li><b>Existe en la tienda</b> — decisión del cliente (2026-08-25): recetable = está en Contabilium
 *       <b>y</b> en TiendaNube. Sin id de la tienda no se le puede crear el cupón, así que el bono
 *       saldría muerto (ver {@code CuponSyncService}).</li>
 * </ol>
 *
 * <p>Criterio ante datos faltantes: <b>ante la duda, publicar</b>. Un producto sin tipo, sin rubro o
 * sin fila en el maestro sigue siendo recetable. Es deliberado: las reglas se evalúan sobre datos de
 * dos sistemas externos que el cliente edita a mano, y un Excel recortado o un rubro renombrado no
 * pueden vaciar el catálogo de un día para el otro. Lo que bloquea es un dato presente y explícito.
 *
 * <p>La regla de la tienda es la excepción —bloquea por un dato <b>ausente</b>— porque ahí la ausencia
 * no es incertidumbre sino un hecho: el producto no está publicado en la tienda y no se puede comprar.
 * Para que siga sin poder vaciar el catálogo, sólo se evalúa cuando el mapeo ya corrió alguna vez
 * ({@code catalogoMapeado}): si ningún producto tiene id de tienda, la regla se apaga sola.
 */
@Component
@RequiredArgsConstructor
public class PublicacionPolicy {

    private final CatalogoProperties props;

    /**
     * Recalcula {@code publicado} sobre el estado actual del producto. Idempotente.
     *
     * @param catalogoMapeado si el mapeo contra TiendaNube ya corrió (hay al menos un producto con
     *                        id de tienda). En false la regla de la tienda no se evalúa, para que un
     *                        catálogo todavía sin mapear no quede entero despublicado.
     */
    public boolean esPublicable(Producto p, boolean catalogoMapeado) {
        return precioValido(p.getPrecio())
                && p.isActivoErp()
                && permitido(props.tiposErpPermitidos(), p.getTipoErp())
                && permitido(props.rubrosPermitidos(), p.getRubroId())
                && !p.isBloqueadoMaestro()
                && estaEnLaTienda(p, catalogoMapeado);
    }

    private static boolean estaEnLaTienda(Producto p, boolean catalogoMapeado) {
        return !catalogoMapeado || p.getTiendanubeProductId() != null;
    }

    /**
     * Por qué este producto no es recetable, en castellano. {@code null} si sí lo es.
     *
     * <p>Devuelve la <b>primera</b> regla que lo bloquea, en el mismo orden en que las evalúa
     * {@link #esPublicable}. Es para el panel del admin: "hay 26 despublicados" no le dice cuál
     * arreglar ni dónde — si el motivo es el precio se corrige en Contabilium, si es el bloqueo se
     * corrige en el Excel.
     */
    public String motivoNoPublicable(Producto p, boolean catalogoMapeado) {
        if (!precioValido(p.getPrecio())) {
            return p.getPrecio() == null
                    ? "Sin precio en Contabilium"
                    : "Precio por debajo del mínimo ($" + props.precioMinimo() + ")";
        }
        if (!p.isActivoErp()) {
            return "Inactivo en Contabilium";
        }
        if (!permitido(props.tiposErpPermitidos(), p.getTipoErp())) {
            return "Tipo \"" + p.getTipoErp() + "\" excluido del catálogo";
        }
        if (!permitido(props.rubrosPermitidos(), p.getRubroId())) {
            return "Rubro fuera de los permitidos (no es producto terminado)";
        }
        if (p.isBloqueadoMaestro()) {
            return "Bloqueado en el maestro de artículos";
        }
        if (!estaEnLaTienda(p, catalogoMapeado)) {
            return "No está publicado en la tienda online";
        }
        return null;
    }

    /** Aplica la política; devuelve true si el valor cambió. */
    public boolean aplicar(Producto p, boolean catalogoMapeado) {
        boolean nuevo = esPublicable(p, catalogoMapeado);
        if (p.isPublicado() == nuevo) {
            return false;
        }
        p.setPublicado(nuevo);
        return true;
    }

    private boolean precioValido(BigDecimal precio) {
        return precio != null && precio.compareTo(props.precioMinimo()) >= 0;
    }

    /**
     * Lista vacía = sin filtro. Valor ausente en el producto (dato que el ERP dejó de mandar, fila
     * vieja) = no bloquea. Solo bloquea un valor presente que no está en la lista.
     */
    private static boolean permitido(List<String> permitidos, String valor) {
        if (permitidos.isEmpty() || valor == null || valor.isBlank()) {
            return true;
        }
        return permitidos.stream().anyMatch(p -> p.equalsIgnoreCase(valor.trim()));
    }
}
