package com.nutriapp.modules.producto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriapp.integrations.IntegrationUnavailableException;
import com.nutriapp.integrations.contabilium.ContabiliumClient;
import com.nutriapp.integrations.contabilium.ContabiliumClient.Concepto;
import com.nutriapp.integrations.contabilium.ContabiliumClient.ConceptoPage;
import com.nutriapp.integrations.health.IntegrationHealthRegistry;
import com.nutriapp.integrations.health.IntegrationHealthRegistry.Proveedor;
import com.nutriapp.modules.producto.dto.SyncProductosResponse;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoSyncServiceTest {

    @Mock ContabiliumClient contabiliumClient;
    @Mock ProductoRepository productoRepository;
    @Mock IntegrationHealthRegistry health;

    @InjectMocks ProductoSyncService service;

    private Concepto concepto(Long id, String sku, String nombre, BigDecimal precioFinal, Integer stock) {
        return new Concepto(id, "P", nombre, sku, "desc " + sku, "activo", null, precioFinal, stock);
    }

    @Test
    void sync_stub_propaga503YRegistraError() {
        when(contabiliumClient.buscarConceptos(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IntegrationUnavailableException("contabilium"));

        assertThatThrownBy(() -> service.sync())
                .isInstanceOf(IntegrationUnavailableException.class);

        verify(health).registrarError(eq(Proveedor.CONTABILIUM), any());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void sync_live_creaNuevoYActualizaExistente() {
        Concepto nuevo = concepto(1L, "SKU-A", "Prod A", new BigDecimal("100"), 5);
        Concepto existenteCambiado = concepto(2L, "SKU-B", "Prod B nuevo", new BigDecimal("20"), 3);
        when(contabiliumClient.buscarConceptos("", 1))
                .thenReturn(new ConceptoPage(List.of(nuevo, existenteCambiado), 1, 2));

        Producto b = new Producto();
        b.setSku("SKU-B");
        b.setNombre("Prod B viejo");
        b.setPrecio(new BigDecimal("10"));
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-A")).thenReturn(Optional.empty());
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-B")).thenReturn(Optional.of(b));

        SyncProductosResponse resp = service.sync();

        assertThat(resp.revisados()).isEqualTo(2);
        assertThat(resp.creados()).isEqualTo(1);
        assertThat(resp.actualizados()).isEqualTo(1);
        assertThat(resp.sinCambios()).isZero();
        assertThat(resp.syncedAt()).isNotNull();
        verify(productoRepository, org.mockito.Mockito.times(2)).save(any(Producto.class));
        verify(health).registrarExito(Proveedor.CONTABILIUM);
    }

    @Test
    void sync_live_existenteSinCambios() {
        Concepto igual = concepto(3L, "SKU-C", "Prod C", new BigDecimal("50"), 7);
        when(contabiliumClient.buscarConceptos("", 1))
                .thenReturn(new ConceptoPage(List.of(igual), 1, 1));

        Producto c = new Producto();
        c.setSku("SKU-C");
        c.setNombre("Prod C");
        c.setDescripcion("desc SKU-C");
        c.setPrecio(new BigDecimal("50"));
        c.setStock(7);
        when(productoRepository.findBySkuAndDeletedAtIsNull("SKU-C")).thenReturn(Optional.of(c));

        SyncProductosResponse resp = service.sync();

        assertThat(resp.revisados()).isEqualTo(1);
        assertThat(resp.creados()).isZero();
        assertThat(resp.actualizados()).isZero();
        assertThat(resp.sinCambios()).isEqualTo(1);
    }

    @Test
    void sync_conceptoSinSku_seSaltea() {
        Concepto sinSku = concepto(4L, null, "Sin código", new BigDecimal("10"), 1);
        when(contabiliumClient.buscarConceptos("", 1))
                .thenReturn(new ConceptoPage(List.of(sinSku), 1, 1));

        SyncProductosResponse resp = service.sync();

        assertThat(resp.revisados()).isEqualTo(1);
        assertThat(resp.creados()).isZero();
        assertThat(resp.actualizados()).isZero();
        assertThat(resp.sinCambios()).isZero();
        verify(productoRepository, never()).findBySkuAndDeletedAtIsNull(any());
        verify(productoRepository, never()).save(any());
    }
}
