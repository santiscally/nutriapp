package com.bonosapp.modules.notificacion.service;

import com.bonosapp.integrations.IntegrationsProperties;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.entity.RecetaItem;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Las dos piezas que el mensaje del bono comparte entre el mail y el link {@code wa.me}: QUÉ
 * producto es el bono y A DÓNDE manda el link. Vive acá (y no duplicada en cada template) porque
 * los dos textos los lee la misma paciente y tienen que decir lo mismo.
 *
 * <p><b>El link es el "link de cupón" de TiendaNube</b> ({@code /discount/<codigo>}), el mismo que
 * la tienda ofrece en el panel del cupón: abrirlo aplica el bono solo, sin que la paciente tenga
 * que copiar el código en el checkout. Es lo que pidió el cliente (F-18, captura en
 * {@code modificaciones post primera entrega/}).
 */
@Component
@RequiredArgsConstructor
public class BonoContenido {

    private final ProductoRepository productos;
    private final IntegrationsProperties integrations;

    /**
     * Nombre del producto del bono, para el "Tu bono profesional de {@code <descripción>}" (F-19).
     *
     * <p>El MVP emite un producto por bono, pero el modelo soporta N: si hay varios se listan
     * separados por " y ". {@code null} si no se pudo resolver ninguno — el texto degrada a la
     * frase sin descripción en vez de decir "de null".
     */
    public String descripcionProductos(Receta receta) {
        Set<String> nombres = new LinkedHashSet<>();
        // Set: dos items del mismo producto no repiten el nombre en el mensaje.
        for (Producto p : productosDe(receta)) {
            String nombre = p.getNombre() == null || p.getNombre().isBlank() ? p.getDescripcion() : p.getNombre();
            if (nombre != null && !nombre.isBlank()) {
                nombres.add(nombre.trim());
            }
        }
        return nombres.isEmpty() ? null : String.join(" y ", nombres);
    }

    /**
     * Link a la ficha del producto en la tienda (S-02), para el "andá directo al producto" de F-18.
     *
     * <p>Sólo cuando el bono tiene <b>un</b> producto y está mapeado a la tienda: con dos, no hay
     * "el" producto al que mandarla, y el link de cupón sigue funcionando igual.
     *
     * <p><b>Ojo, no reemplaza a {@link #linkCupon(Receta)}:</b> abrir la ficha del producto no
     * aplica el bono. Verificado contra la tienda real (2026-09-21): TiendaNube ignora los
     * parámetros de redirect en {@code /discount/<codigo>} y siempre cae en la home, así que
     * <b>no existe</b> un único link que aplique el cupón y aterrice en el producto. Por eso el
     * mensaje manda primero a activar el bono y después al producto.
     */
    public String linkProducto(Receta receta) {
        List<Producto> items = productosDe(receta);
        if (items.size() != 1) {
            return null;
        }
        return integrations.tiendanube().urlDeProducto(items.get(0).getTiendanubeHandle());
    }

    private List<Producto> productosDe(Receta receta) {
        if (receta == null || receta.getItems().isEmpty()) {
            return List.of();
        }
        List<UUID> ids = receta.getItems().stream()
                .map(RecetaItem::getProductoId)
                .filter(Objects::nonNull)
                .toList();
        return ids.isEmpty() ? List.of() : productos.findAllById(ids);
    }

    /**
     * Link de cupón: abrirlo deja el bono aplicado en la tienda. {@code null} si no hay tienda
     * configurada ({@code TIENDANUBE_STORE_URL} vacío) — el mensaje sale igual, sin link roto.
     */
    public String linkCupon(Receta receta) {
        return receta == null ? null : linkCupon(receta.getCodigo());
    }

    /** Igual, desde el código del cupón: lo que tiene a mano quien arma el PDF (F-21). */
    public String linkCupon(String codigo) {
        String tienda = integrations.tiendanube().storeUrlNormalizada();
        if (tienda == null || codigo == null || codigo.isBlank()) {
            return null;
        }
        return tienda + "/discount/" + codigo;
    }

    /** Vitrina pública, para cuando no hay código de cupón que linkear. */
    public String tienda() {
        return integrations.tiendanube().storeUrlNormalizada();
    }

    /** La frase que acompaña al link, igual en el mail y en el WhatsApp (F-18, texto del cliente). */
    public static final String INSTRUCCION_LINK =
            "Dale click al link y sumá el producto al carrito, y automáticamente estará aplicado tu "
                    + "bono (No combinable con promociones activas)";
}
