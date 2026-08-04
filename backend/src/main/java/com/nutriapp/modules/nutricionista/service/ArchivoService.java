package com.nutriapp.modules.nutricionista.service;

import com.nutriapp.common.error.ConflictException;
import com.nutriapp.common.error.NotFoundException;
import com.nutriapp.modules.nutricionista.entity.NutricionistaArchivo;
import com.nutriapp.modules.nutricionista.entity.TipoArchivo;
import com.nutriapp.modules.nutricionista.repository.NutricionistaArchivoRepository;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * C-08 + C-17 — guarda y sirve los archivos de una nutricionista (matrícula y foto de perfil).
 *
 * <p>Reglas duras, iguales para los dos tipos:
 * <ul>
 *   <li>Se valida el <b>content-type</b> contra una whitelist. Nada de aceptar cualquier cosa
 *       porque la extensión diga PDF.</li>
 *   <li>Tope de tamaño por tipo: un PDF de matrícula no necesita más de 5 MB, y una foto sacada
 *       del celular hay que <b>achicarla</b>, no guardarla tal cual.</li>
 *   <li>Subir de nuevo <b>reemplaza</b> el anterior (índice único por nutricionista+tipo).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchivoService {

    /** La matrícula suele ser un PDF escaneado o una foto del título. */
    private static final Set<String> TIPOS_MATRICULA =
            Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final Set<String> TIPOS_FOTO = Set.of("image/jpeg", "image/png", "image/webp");

    private static final int MAX_MATRICULA_BYTES = 5 * 1024 * 1024;
    private static final int MAX_FOTO_SUBIDA_BYTES = 8 * 1024 * 1024;
    /** La foto se guarda como thumbnail: es un círculo de 36px en el navbar, no un póster. */
    private static final int FOTO_LADO_MAX = 256;

    private final NutricionistaArchivoRepository repo;

    @Transactional
    public NutricionistaArchivo guardar(UUID nutricionistaId, TipoArchivo tipo, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ConflictException("El archivo está vacío");
        }
        boolean esFoto = tipo == TipoArchivo.FOTO_PERFIL;
        String contentType = normalizar(file.getContentType());
        Set<String> permitidos = esFoto ? TIPOS_FOTO : TIPOS_MATRICULA;
        if (!permitidos.contains(contentType)) {
            throw new ConflictException(esFoto
                    ? "La foto tiene que ser JPG, PNG o WEBP"
                    : "El archivo tiene que ser un PDF o una imagen (JPG, PNG o WEBP)");
        }
        int maximo = esFoto ? MAX_FOTO_SUBIDA_BYTES : MAX_MATRICULA_BYTES;
        if (file.getSize() > maximo) {
            throw new ConflictException("El archivo supera el máximo de " + (maximo / (1024 * 1024)) + " MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ConflictException("No se pudo leer el archivo");
        }
        if (esFoto) {
            bytes = achicar(bytes);
            contentType = "image/jpeg"; // el redimensionado siempre sale JPEG
        }

        // Reemplazo: el índice único es por (nutricionista, tipo) entre los vivos.
        repo.findByNutricionistaIdAndTipoAndDeletedAtIsNull(nutricionistaId, tipo)
                .ifPresent(previo -> {
                    previo.setDeletedAt(Instant.now());
                    repo.save(previo);
                });
        repo.flush();

        NutricionistaArchivo a = new NutricionistaArchivo();
        a.setNutricionistaId(nutricionistaId);
        a.setTipo(tipo);
        a.setNombreOriginal(file.getOriginalFilename());
        a.setContentType(contentType);
        a.setTamanoBytes(bytes.length);
        a.setContenido(bytes);
        // saveAndFlush, no save: si el INSERT falla (tipo de columna, tamaño, lo que sea) queremos
        // que reviente ACÁ y no recién en el commit. En el registro público, un error en el commit
        // cae fuera del try/catch de RegistroService y deja el usuario de Keycloak huérfano.
        NutricionistaArchivo guardado = repo.saveAndFlush(a);
        log.info("[archivos] {} de nutricionista {} guardado ({} bytes, {})",
                tipo, nutricionistaId, bytes.length, contentType);
        return guardado;
    }

    @Transactional(readOnly = true)
    public NutricionistaArchivo obtener(UUID nutricionistaId, TipoArchivo tipo) {
        return repo.findByNutricionistaIdAndTipoAndDeletedAtIsNull(nutricionistaId, tipo)
                .orElseThrow(() -> new NotFoundException("No hay archivo cargado"));
    }

    @Transactional(readOnly = true)
    public Optional<NutricionistaArchivo> buscar(UUID nutricionistaId, TipoArchivo tipo) {
        return repo.findByNutricionistaIdAndTipoAndDeletedAtIsNull(nutricionistaId, tipo);
    }

    /** Quiénes del lote tienen archivo de este tipo (para el listado, sin traer los bytes). */
    @Transactional(readOnly = true)
    public List<UUID> conArchivo(TipoArchivo tipo, List<UUID> ids) {
        return ids.isEmpty() ? List.of() : repo.idsConArchivo(tipo, ids);
    }

    /**
     * Foto como data URI, para mandarla embebida en el JSON de {@code /me}. Es un thumbnail de
     * pocos KB: evita un segundo request con Bearer y que el front tenga que manejar object URLs
     * sólo para pintar el avatar.
     */
    @Transactional(readOnly = true)
    public String fotoDataUri(UUID nutricionistaId) {
        return buscar(nutricionistaId, TipoArchivo.FOTO_PERFIL)
                .map(a -> "data:" + a.getContentType() + ";base64,"
                        + Base64.getEncoder().encodeToString(a.getContenido()))
                .orElse(null);
    }

    /**
     * Borrado físico de todos los archivos de una nutricionista. Sólo lo usa la baja definitiva
     * del admin: acá el soft-delete no alcanza, porque las filas tienen FK a `nutricionistas` y
     * quedarían apuntando a alguien que ya no existe.
     */
    @Transactional
    public void borrarTodos(UUID nutricionistaId) {
        repo.deleteByNutricionistaId(nutricionistaId);
    }

    @Transactional
    public void borrar(UUID nutricionistaId, TipoArchivo tipo) {
        repo.findByNutricionistaIdAndTipoAndDeletedAtIsNull(nutricionistaId, tipo)
                .ifPresent(a -> {
                    a.setDeletedAt(Instant.now());
                    repo.save(a);
                });
    }

    /** Reduce la imagen para que su lado mayor no supere {@link #FOTO_LADO_MAX}, y la pasa a JPEG. */
    private byte[] achicar(byte[] original) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) {
                throw new ConflictException("No pudimos leer la imagen. Probá con otro archivo.");
            }
            int lado = Math.max(src.getWidth(), src.getHeight());
            if (lado <= FOTO_LADO_MAX && esJpeg(original)) {
                return original; // ya es chica: no la re-comprimimos y perdemos calidad al pedo
            }
            double escala = Math.min(1.0, (double) FOTO_LADO_MAX / lado);
            int w = Math.max(1, (int) Math.round(src.getWidth() * escala));
            int h = Math.max(1, (int) Math.round(src.getHeight() * escala));

            // Fondo blanco: los PNG con transparencia salen negros al pasar a JPEG.
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            var g = dst.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(dst, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ConflictException("No pudimos procesar la imagen. Probá con otro archivo.");
        }
    }

    private static boolean esJpeg(byte[] b) {
        return b.length > 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    private static String normalizar(String contentType) {
        if (contentType == null) {
            return "";
        }
        int p = contentType.indexOf(';'); // "image/jpeg; charset=..." → "image/jpeg"
        return (p >= 0 ? contentType.substring(0, p) : contentType).trim().toLowerCase();
    }
}
