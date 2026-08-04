package com.nutriapp.modules.producto.controller;

import com.nutriapp.modules.producto.maestro.ImportarMaestroResponse;
import com.nutriapp.modules.producto.maestro.MaestroEstadoResponse;
import com.nutriapp.modules.producto.maestro.MaestroImportService;
import lombok.RequiredArgsConstructor;
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
