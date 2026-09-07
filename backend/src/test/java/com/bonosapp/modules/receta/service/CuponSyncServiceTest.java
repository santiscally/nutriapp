package com.bonosapp.modules.receta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bonosapp.integrations.IntegrationUnavailableException;
import com.bonosapp.integrations.health.IntegrationHealthRegistry;
import com.bonosapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.bonosapp.integrations.tiendanube.TiendaNubeClient;
import com.bonosapp.modules.producto.entity.Producto;
import com.bonosapp.modules.producto.repository.ProductoRepository;
import com.bonosapp.modules.receta.dto.ResyncCuponesResponse;
import com.bonosapp.modules.receta.entity.CuponSyncEstado;
import com.bonosapp.modules.receta.entity.EstadoReceta;
import com.bonosapp.modules.receta.entity.Receta;
import com.bonosapp.modules.receta.entity.RecetaItem;
import com.bonosapp.modules.receta.repository.RecetaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuponSyncServiceTest {

    @Mock RecetaRepository repo;
    @Mock ProductoRepository productoRepository;
    @Mock TiendaNubeClient tiendaNubeClient;
    @Mock IntegrationHealthRegistry health;

    @InjectMocks CuponSyncService service;

    private Receta receta(String codigo) {
        Receta r = new Receta();
        r.setId(UUID.randomUUID());
        r.setCodigo(codigo);
        r.setEstado(EstadoReceta.PENDIENTE);
        r.setCuponSyncEstado(CuponSyncEstado.PENDIENTE);
        conItemMapeado(r);
        return r;
    }

    /** Una receta usable en live: su producto ya tiene el id de TiendaNube escrito. */
    private void conItemMapeado(Receta r) {
        UUID productoId = UUID.randomUUID();
        RecetaItem item = new RecetaItem();
        item.setProductoId(productoId);
        r.addItem(item);
        Producto p = new Producto();
        p.setNombre("ON-ROLL FEM X 60G");
        p.setTiendanubeProductId(363154002L);
        p.setTiendanubeVariantId(1583970721L);
        when(productoRepository.findById(productoId)).thenReturn(Optional.of(p));
    }

    private Receta recetaConProductoSinMapear(String codigo) {
        Receta r = new Receta();
        r.setId(UUID.randomUUID());
        r.setCodigo(codigo);
        r.setEstado(EstadoReceta.PENDIENTE);
        r.setCuponSyncEstado(CuponSyncEstado.PENDIENTE);
        UUID productoId = UUID.randomUUID();
        RecetaItem item = new RecetaItem();
        item.setProductoId(productoId);
        r.addItem(item);
        Producto p = new Producto();
        p.setNombre("PRODUCTO SIN MAPEAR");
        when(productoRepository.findById(productoId)).thenReturn(Optional.of(p));
        return r;
    }

    @Test
    void registrar_live_marcaSincronizadoYRegistraExito() {
        Receta r = receta("RX-AAA");
        when(tiendaNubeClient.createCoupon(any()))
                .thenReturn(new TiendaNubeClient.Coupon(999L, "RX-AAA", true));

        service.registrar(r);

        assertThat(r.getCuponSyncEstado()).isEqualTo(CuponSyncEstado.SINCRONIZADO);
        assertThat(r.getCuponTiendanubeId()).isEqualTo(999L);
        assertThat(r.getCuponSyncError()).isNull();
        verify(health).registrarExito(Proveedor.TIENDANUBE);
    }

    @Test
    void registrar_stub_degradaAPendienteSinRomper() {
        Receta r = receta("RX-BBB");
        when(tiendaNubeClient.createCoupon(any()))
                .thenThrow(new IntegrationUnavailableException("tiendanube"));

        service.registrar(r); // no propaga

        assertThat(r.getCuponSyncEstado()).isEqualTo(CuponSyncEstado.PENDIENTE);
        assertThat(r.getCuponSyncError()).contains("tiendanube");
        verify(health).registrarError(eq(Proveedor.TIENDANUBE), any());
    }

    @Test
    void resync_stub_todasSiguenPendientes() {
        List<Receta> pendientes = List.of(receta("RX-1"), receta("RX-2"));
        when(repo.findResyncables(any(Pageable.class))).thenReturn(pendientes);
        when(tiendaNubeClient.createCoupon(any()))
                .thenThrow(new IntegrationUnavailableException("tiendanube"));
        when(repo.countResyncables()).thenReturn(2L);

        ResyncCuponesResponse resp = service.resync();

        assertThat(resp.intentados()).isEqualTo(2);
        assertThat(resp.sincronizados()).isZero();
        assertThat(resp.pendientes()).isEqualTo(2L);
        verify(repo, org.mockito.Mockito.times(2)).save(any(Receta.class));
    }

    @Test
    void resync_live_sincronizaYActualizaPendientesRestantes() {
        List<Receta> pendientes = List.of(receta("RX-1"), receta("RX-2"));
        when(repo.findResyncables(any(Pageable.class))).thenReturn(pendientes);
        when(tiendaNubeClient.createCoupon(any()))
                .thenReturn(new TiendaNubeClient.Coupon(1L, "RX", true));
        when(repo.countResyncables()).thenReturn(0L);

        ResyncCuponesResponse resp = service.resync();

        assertThat(resp.intentados()).isEqualTo(2);
        assertThat(resp.sincronizados()).isEqualTo(2);
        assertThat(resp.pendientes()).isZero();
    }

    @Test
    void resync_errorNoTransitorio_marcaErrorYSigue() {
        Receta r = receta("RX-X");
        when(repo.findResyncables(any(Pageable.class))).thenReturn(List.of(r));
        when(tiendaNubeClient.createCoupon(any())).thenThrow(new RuntimeException("HTTP 422"));
        when(repo.countResyncables()).thenReturn(1L);

        ResyncCuponesResponse resp = service.resync();

        assertThat(r.getCuponSyncEstado()).isEqualTo(CuponSyncEstado.ERROR);
        assertThat(r.getCuponSyncError()).contains("422");
        assertThat(resp.intentados()).isEqualTo(1);
        assertThat(resp.sincronizados()).isZero();
    }

    @Test
    void noRegistraElCuponSiUnProductoNoEstaMapeadoATiendaNube() {
        Receta r = recetaConProductoSinMapear("RX-SIN-MAPEO");

        service.registrar(r);

        assertThat(r.getCuponSyncEstado()).isEqualTo(CuponSyncEstado.PENDIENTE);
        assertThat(r.getCuponSyncError()).contains("Sin mapeo a TiendaNube", "PRODUCTO SIN MAPEAR");
        assertThat(r.getCuponTiendanubeId()).isNull();
        verify(tiendaNubeClient, never()).createCoupon(any());
    }

    @Test
    void mandaElProductIdDelProductoRecetado() {
        Receta r = receta("RX-OK");
        when(tiendaNubeClient.createCoupon(any()))
                .thenReturn(new TiendaNubeClient.Coupon(1L, "RX-OK", true));

        service.registrar(r);

        org.mockito.ArgumentCaptor<TiendaNubeClient.CouponRequest> captor =
                org.mockito.ArgumentCaptor.forClass(TiendaNubeClient.CouponRequest.class);
        verify(tiendaNubeClient).createCoupon(captor.capture());
        // product id, NO variant id: con variant id la API responde 422 (verificado en vivo).
        assertThat(captor.getValue().productIds()).containsExactly(363154002L);
    }

    @Test
    void elMensajeAlUsuarioNoPrometeUnReintentoQueNuncaVaAFuncionar() {
        Receta r = recetaConProductoSinMapear("RX-MSG");

        service.registrar(r);

        String mensaje = r.getCuponSyncEstado().mensajeDegradacion(r.getCuponSyncError());
        assertThat(mensaje).contains("no está publicado en la tienda").doesNotContain("no está disponible");
    }

    @Test
    void laCaidaDeLaIntegracionSiPrometeReintento() {
        Receta r = receta("RX-CAIDA");
        when(tiendaNubeClient.createCoupon(any()))
                .thenThrow(new IntegrationUnavailableException("tiendanube"));

        service.registrar(r);

        assertThat(r.getCuponSyncEstado().mensajeDegradacion(r.getCuponSyncError()))
                .contains("Se reintenta automáticamente");
    }
}
