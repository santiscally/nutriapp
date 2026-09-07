package com.bonosapp.modules.producto.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bonosapp.modules.producto.CatalogoProperties;
import com.bonosapp.modules.producto.entity.Producto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regla de negocio central: qué producto se puede recetar y por qué no. */
class PublicacionPolicyTest {

    private static final boolean MAPEADO = true;
    private static final boolean SIN_MAPEAR_NUNCA = false;

    private final PublicacionPolicy policy = new PublicacionPolicy(
            new CatalogoProperties(List.of(), List.of(), new BigDecimal("100"), 0));

    /** Producto sano: en el ERP, con precio y presente en la tienda. */
    private static Producto producto() {
        Producto p = new Producto();
        p.setNombre("AGIAL X 300 G");
        p.setPrecio(new BigDecimal("28774.00"));
        p.setActivoErp(true);
        p.setTiendanubeProductId(363174331L);
        return p;
    }

    @Test
    void enContabiliumYEnTiendaNubeEsRecetable() {
        assertThat(policy.esPublicable(producto(), MAPEADO)).isTrue();
        assertThat(policy.motivoNoPublicable(producto(), MAPEADO)).isNull();
    }

    /** Decisión del cliente: sin producto en la tienda no hay compra posible, así que no se receta. */
    @Test
    void siNoEstaEnLaTiendaNoEsRecetable() {
        Producto p = producto();
        p.setTiendanubeProductId(null);

        assertThat(policy.esPublicable(p, MAPEADO)).isFalse();
        assertThat(policy.motivoNoPublicable(p, MAPEADO)).isEqualTo("No está publicado en la tienda online");
    }

    /**
     * Guard: mientras el mapeo no haya corrido nunca, TODOS los productos tienen el id en null.
     * Si la regla se evaluara igual, la primera sync dejaría el catálogo entero despublicado y
     * ninguna nutricionista podría recetar nada.
     */
    @Test
    void conElCatalogoSinMapearLaReglaDeTiendaNoSeAplica() {
        Producto p = producto();
        p.setTiendanubeProductId(null);

        assertThat(policy.esPublicable(p, SIN_MAPEAR_NUNCA)).isTrue();
        assertThat(policy.motivoNoPublicable(p, SIN_MAPEAR_NUNCA)).isNull();
    }

    /** El id de tienda no salva a un producto que ya está bloqueado por otra regla. */
    @Test
    void estarEnLaTiendaNoAlcanzaSiFallaOtraRegla() {
        Producto p = producto();
        p.setActivoErp(false);

        assertThat(policy.esPublicable(p, MAPEADO)).isFalse();
        assertThat(policy.motivoNoPublicable(p, MAPEADO)).isEqualTo("Inactivo en Contabilium");
    }

    /** El motivo devuelve la PRIMERA regla que bloquea: el precio se corrige antes que la tienda. */
    @Test
    void conVariasReglasRotasElMotivoEsElPrimero() {
        Producto p = producto();
        p.setPrecio(new BigDecimal("1.00"));
        p.setTiendanubeProductId(null);

        assertThat(policy.motivoNoPublicable(p, MAPEADO)).contains("Precio por debajo del mínimo");
    }

    @Test
    void aplicarDevuelveTrueSoloCuandoElValorCambia() {
        Producto p = producto();
        p.setPublicado(false);

        assertThat(policy.aplicar(p, MAPEADO)).isTrue();
        assertThat(p.isPublicado()).isTrue();
        assertThat(policy.aplicar(p, MAPEADO)).isFalse();
    }

    /** Mapear un producto lo vuelve recetable sin tocar nada más. */
    @Test
    void mapearloLoVuelveRecetable() {
        Producto p = producto();
        p.setTiendanubeProductId(null);
        policy.aplicar(p, MAPEADO);
        assertThat(p.isPublicado()).isFalse();

        p.setTiendanubeProductId(363174331L);

        assertThat(policy.aplicar(p, MAPEADO)).isTrue();
        assertThat(p.isPublicado()).isTrue();
    }
}
