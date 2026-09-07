package com.bonosapp.modules.producto.controller;

import com.bonosapp.common.dto.PageResponse;
import com.bonosapp.modules.producto.dto.AdminProductoResponse;
import com.bonosapp.modules.producto.dto.CatalogoResumenResponse;
import com.bonosapp.modules.producto.maestro.ImportarMaestroResponse;
import com.bonosapp.modules.producto.maestro.MaestroEstadoResponse;
import com.bonosapp.modules.producto.maestro.MaestroImportService;
import com.bonosapp.modules.producto.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Operaciones de catálogo reservadas al ADMIN. Hoy: la carga del maestro de artículos de TBC (C-12),
 * el "examinar → insertar el Excel" que pidió Gon. Va al lado del sync de Contabilium en la pantalla
 * de integraciones, y el orden operativo sugerido es sincronizar primero y después importar.
 *
 * <p>Es <b>manual a demanda</b>, no un job: decisión explícita del cliente (call 30:46, <i>"amerita un
 * botón a demanda, no pongas algo diario"</i>).
 */
@RestController
@RequestMapping("/api/v1/admin/productos")
@RequiredArgsConstructor
public class AdminCatalogoController {

    private final MaestroImportService maestroImportService;
    private final ProductoService productoService;

    /**
     * Catálogo completo, incluidos los que no se pueden recetar y con el motivo. El buscador de
     * recetas sólo muestra publicados: acá la pregunta es la contraria, qué quedó afuera.
     *
     * @param sinMaestro sólo los que no matchearon contra el Excel de TBC (62 en el import real).
     * @param publicado  true/false para filtrar por estado; omitido = todos.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('admin:manage')")
    public PageResponse<AdminProductoResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "false") boolean sinMaestro,
            @RequestParam(required = false) Boolean publicado,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(
                productoService.searchAdmin(q, departamento, categoria, sinMaestro, publicado, pageable));
    }

    /** Conteos del catálogo (total / publicados / sin maestro / bloqueados) para las tarjetas. */
    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('admin:manage')")
    public CatalogoResumenResponse resumen() {
        return productoService.resumen();
    }

    /**
     * Importa el maestro sobre el catálogo ya sincronizado. Síncrono: parsear 2225 filas y hacer el
     * upsert lleva segundos (a diferencia del sync de Contabilium, que son 50 requests HTTP throttled),
     * y el admin necesita ver el resultado del archivo que acaba de subir.
     *
     * @return el reporte: cuántas filas se aplicaron, cuántas no matchearon y cuáles.
     */
    @PostMapping(value = "/importar-maestro", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('admin:manage')")
    public ImportarMaestroResponse importarMaestro(@RequestParam("archivo") MultipartFile archivo) {
        return maestroImportService.importar(archivo);
    }

    /** Última importación, para mostrar "importado el …" junto al botón. Todo null = nunca se importó. */
    @GetMapping("/maestro/estado")
    @PreAuthorize("hasAuthority('admin:manage')")
    public MaestroEstadoResponse estadoMaestro() {
        return maestroImportService.estado();
    }
}
