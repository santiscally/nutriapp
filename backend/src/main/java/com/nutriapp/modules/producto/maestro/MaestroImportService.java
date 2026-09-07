package com.nutriapp.modules.producto.maestro;

import com.nutriapp.common.error.UnprocessableException;
import com.nutriapp.modules.producto.CatalogoProperties;
import com.nutriapp.modules.producto.entity.Producto;
import com.nutriapp.modules.producto.entity.ProductoTag;
import com.nutriapp.modules.producto.repository.ProductoRepository;
import com.nutriapp.modules.producto.service.PublicacionPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * C-12 — Importa el maestro de artículos de TBC sobre el catálogo ya sincronizado desde Contabilium.
 *
 * <p>El cruce es por SKU (= {@code Codigo} de Contabilium), verificado contra el archivo real: 2163 de
 * 2225 filas matchean, y en las que matchean el {@code ID CONTABILIUM} del Excel coincide 1:1 con el
 * {@code contabilium_id} de la DB. Esa segunda clave se usa como control: si para un SKU los dos no
 * coinciden, la fila se rechaza en vez de escribir los datos de un producto sobre otro.
 *
 * <p><b>Escribe solo los campos cuyo dueño es el maestro</b> (ver 07-...md §3.2). Nunca toca nombre,
 * precio, stock ni marca: esos son de Contabilium. Por eso importar y sincronizar son conmutativos y
 * el admin no puede romper nada haciéndolo en el orden "equivocado".
 *
 * <p>Todo en una transacción: 2225 filas ya parseadas en memoria, sin llamadas externas de por medio.
 * Un import a medias sería peor que uno fallido — el admin no tendría forma de saber hasta dónde llegó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaestroImportService {

    private final MaestroXlsxParser parser;
    private final ProductoRepository productoRepository;
    private final MaestroImportacionRepository importacionRepository;
    private final PublicacionPolicy publicacionPolicy;
    private final CatalogoProperties props;

    /** Largos de las columnas en la DB: recortamos acá para que una celda larga no aborte el import. */
    private static final int LEN_DEPARTAMENTO = 120;
    private static final int LEN_CATEGORIA = 120;
    private static final int LEN_SUBCATEGORIA = 160;
    private static final int LEN_LABORATORIO = 120;
    private static final int LEN_IMAGEN_URL = 500;

    /** Última importación, para el panel de integraciones. Nunca importado → todo null. */
    @Transactional(readOnly = true)
    public MaestroEstadoResponse estado() {
        Instant catalogo = productoRepository.maxMaestroSyncedAt();
        return importacionRepository.findFirstByOrderByCreatedAtDesc()
                .map(i -> new MaestroEstadoResponse(i.getCreatedAt(), i.getNombreArchivo(),
                        i.getFilasLeidas(), i.getFilasMatcheadas(), i.getFilasSinMatch(),
                        i.getFilasRechazadas(), catalogo))
                .orElseGet(() -> MaestroEstadoResponse.vacio(catalogo));
    }

    @Transactional
    public ImportarMaestroResponse importar(MultipartFile archivo) {
        validarArchivo(archivo);
        List<MaestroFila> filas;
        try (InputStream in = archivo.getInputStream()) {
            filas = parser.parsear(in);
        } catch (IOException ex) {
            throw new UnprocessableException("No se pudo leer el archivo subido.");
        }
        if (filas.isEmpty()) {
            throw new UnprocessableException("El archivo no tiene ninguna fila con SKU.");
        }
        return aplicar(filas, archivo.getOriginalFilename(), archivo.getSize());
    }

    private ImportarMaestroResponse aplicar(List<MaestroFila> filas, String nombreArchivo, long tamano) {
        Instant ahora = Instant.now();
        Map<String, Producto> porSku = new HashMap<>();
        for (Producto p : productoRepository.findParaImportacion(
                filas.stream().map(MaestroFila::sku).collect(java.util.stream.Collectors.toSet()))) {
            porSku.put(p.getSku(), p);
        }

        // Una vez por importación, no por fila (son ~2225).
        boolean catalogoMapeado = productoRepository.existsByTiendanubeProductIdIsNotNullAndDeletedAtIsNull();
        Set<String> skusVistos = new HashSet<>();
        List<String> sinMatch = new ArrayList<>();
        List<String> rechazos = new ArrayList<>();
        int matcheadas = 0;
        int actualizadas = 0;
        int publicados = 0;
        int despublicados = 0;

        for (MaestroFila f : filas) {
            if (!skusVistos.add(f.sku())) {
                rechazos.add("fila " + f.fila() + ": el SKU " + f.sku() + " aparece más de una vez");
                continue;
            }
            Producto p = porSku.get(f.sku());
            if (p == null) {
                sinMatch.add(f.sku());
                continue;
            }
            if (f.idContabilium() != null && p.getContabiliumId() != null
                    && !f.idContabilium().equals(p.getContabiliumId())) {
                rechazos.add("fila " + f.fila() + ": el SKU " + f.sku() + " apunta al ID Contabilium "
                        + f.idContabilium() + " y en el catálogo es " + p.getContabiliumId());
                continue;
            }
            matcheadas++;
            boolean publicadoAntes = p.isPublicado();
            if (escribir(p, f, ahora, catalogoMapeado)) {
                actualizadas++;
            }
            if (p.isPublicado() != publicadoAntes) {
                if (p.isPublicado()) {
                    publicados++;
                } else {
                    despublicados++;
                }
            }
        }
        productoRepository.saveAll(porSku.values());

        ImportarMaestroResponse r = new ImportarMaestroResponse(
                filas.size(), matcheadas, actualizadas, sinMatch.size(), rechazos.size(),
                muestra(sinMatch), muestra(rechazos), publicados, despublicados, ahora,
                mensaje(filas.size(), matcheadas, sinMatch.size(), rechazos.size(), despublicados));
        registrar(r, nombreArchivo, tamano, sinMatch, rechazos);
        log.info("[maestro-import] archivo={} filas={} matcheadas={} actualizadas={} sinMatch={} rechazadas={}",
                nombreArchivo, filas.size(), matcheadas, actualizadas, sinMatch.size(), rechazos.size());
        return r;
    }

    /** Copia los campos del maestro; devuelve true si algo cambió. */
    private boolean escribir(Producto p, MaestroFila f, Instant ahora, boolean catalogoMapeado) {
        String departamento = recortar(f.departamento(), LEN_DEPARTAMENTO);
        String categoria = recortar(f.categoria(), LEN_CATEGORIA);
        String subcategoria = recortar(f.subcategoria(), LEN_SUBCATEGORIA);
        String laboratorio = recortar(f.laboratorio(), LEN_LABORATORIO);
        String imagenUrl = recortar(f.imagenUrl(), LEN_IMAGEN_URL);
        Set<ProductoTag> tags = tags(f);

        boolean cambio = false;
        cambio |= !Objects.equals(p.getDepartamento(), departamento);
        cambio |= !Objects.equals(p.getCategoria(), categoria);
        cambio |= !Objects.equals(p.getSubcategoria(), subcategoria);
        cambio |= !Objects.equals(p.getLaboratorio(), laboratorio);
        cambio |= !Objects.equals(p.getDescripcionWeb(), f.descripcionWeb());
        cambio |= !Objects.equals(p.getImagenUrl(), imagenUrl);
        cambio |= p.isBloqueadoMaestro() != f.bloqueado();
        cambio |= !p.getTags().equals(tags);

        p.setDepartamento(departamento);
        p.setCategoria(categoria);
        p.setSubcategoria(subcategoria);
        p.setLaboratorio(laboratorio);
        p.setDescripcionWeb(f.descripcionWeb());
        p.setImagenUrl(imagenUrl);
        p.setBloqueadoMaestro(f.bloqueado());
        // Reemplazo, no merge: la planilla es la fuente de verdad de los tags. Si TBC borra un tag,
        // tiene que desaparecer del buscador; mergeando quedaría para siempre.
        if (!p.getTags().equals(tags)) {
            p.getTags().clear();
            p.getTags().addAll(tags);
        }
        p.setMaestroSyncedAt(ahora);
        cambio |= publicacionPolicy.aplicar(p, catalogoMapeado);
        return cambio;
    }

    private Set<ProductoTag> tags(MaestroFila f) {
        Set<ProductoTag> out = new LinkedHashSet<>();
        for (String raw : f.tags()) {
            ProductoTag t = ProductoTag.of(raw);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new UnprocessableException("No llegó ningún archivo.");
        }
        if (archivo.getSize() > props.importMaxBytes()) {
            throw new UnprocessableException("El archivo supera el máximo de "
                    + (props.importMaxBytes() / (1024 * 1024)) + " MB.");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".xlsx")) {
            throw new UnprocessableException(
                    "El archivo tiene que ser un Excel .xlsx (si lo tenés en .xls, guardalo como .xlsx).");
        }
    }

    private void registrar(ImportarMaestroResponse r, String nombreArchivo, long tamano,
                           List<String> sinMatch, List<String> rechazos) {
        MaestroImportacion imp = new MaestroImportacion();
        imp.setNombreArchivo(nombreArchivo);
        imp.setTamanoBytes(tamano);
        imp.setFilasLeidas(r.filasLeidas());
        imp.setFilasMatcheadas(r.filasMatcheadas());
        imp.setFilasActualizadas(r.filasActualizadas());
        imp.setFilasSinMatch(r.filasSinMatch());
        imp.setFilasRechazadas(r.filasRechazadas());
        imp.setDetalle(detalle(sinMatch, rechazos));
        importacionRepository.save(imp);
    }

    private static String detalle(List<String> sinMatch, List<String> rechazos) {
        StringBuilder sb = new StringBuilder();
        if (!sinMatch.isEmpty()) {
            sb.append("SKUs sin producto en el catálogo: ").append(String.join(", ", sinMatch));
        }
        if (!rechazos.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Filas rechazadas:\n").append(String.join("\n", rechazos));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String mensaje(int leidas, int matcheadas, int sinMatch, int rechazadas, int despublicados) {
        StringBuilder sb = new StringBuilder("Archivo importado: ")
                .append(matcheadas).append(" de ").append(leidas)
                .append(matcheadas == 1 ? " fila aplicada" : " filas aplicadas").append(" al catálogo");
        if (sinMatch > 0) {
            sb.append("; ").append(sinMatch).append(" sin producto en el catálogo");
        }
        if (rechazadas > 0) {
            sb.append("; ").append(rechazadas).append(" rechazadas");
        }
        if (despublicados > 0) {
            sb.append("; ").append(despublicados).append(" dejaron de estar disponibles para recetar");
        }
        return sb.append('.').toString();
    }

    private static List<String> muestra(List<String> xs) {
        return xs.size() <= ImportarMaestroResponse.MUESTRA_MAXIMA
                ? List.copyOf(xs)
                : List.copyOf(xs.subList(0, ImportarMaestroResponse.MUESTRA_MAXIMA));
    }

    private static String recortar(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
