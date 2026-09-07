package com.bonosapp.modules.nutricionista.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bonosapp.common.error.ConflictException;
import com.bonosapp.modules.nutricionista.entity.NutricionistaArchivo;
import com.bonosapp.modules.nutricionista.entity.TipoArchivo;
import com.bonosapp.modules.nutricionista.repository.NutricionistaArchivoRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

/** C-08 + C-17 — validación y redimensionado de archivos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchivoServiceTest {

    @Mock NutricionistaArchivoRepository repo;

    @InjectMocks ArchivoService service;

    private static final UUID NUTRI = UUID.randomUUID();

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Devuelve el archivo que se mandó a guardar. */
    private NutricionistaArchivo capturar() {
        ArgumentCaptor<NutricionistaArchivo> c = ArgumentCaptor.forClass(NutricionistaArchivo.class);
        org.mockito.Mockito.verify(repo).saveAndFlush(c.capture());
        return c.getValue();
    }

    @Test
    void foto_grande_se_achica_y_se_guarda_como_jpeg() throws IOException {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        byte[] original = png(800, 600);

        service.guardar(NUTRI, TipoArchivo.FOTO_PERFIL,
                new MockMultipartFile("foto", "yo.png", "image/png", original));

        NutricionistaArchivo a = capturar();
        assertThat(a.getContentType()).isEqualTo("image/jpeg");
        assertThat(a.getContenido().length).isLessThan(original.length);
        BufferedImage guardada = ImageIO.read(new ByteArrayInputStream(a.getContenido()));
        // Lado mayor acotado a 256, y la proporción se respeta (800x600 → 256x192).
        assertThat(Math.max(guardada.getWidth(), guardada.getHeight())).isEqualTo(256);
        assertThat(guardada.getWidth()).isEqualTo(256);
        assertThat(guardada.getHeight()).isEqualTo(192);
    }

    @Test
    void foto_chica_no_se_agranda() throws IOException {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.guardar(NUTRI, TipoArchivo.FOTO_PERFIL,
                new MockMultipartFile("foto", "chica.png", "image/png", png(64, 64)));

        BufferedImage guardada = ImageIO.read(new ByteArrayInputStream(capturar().getContenido()));
        assertThat(guardada.getWidth()).isEqualTo(64);
    }

    @Test
    void la_foto_no_acepta_pdf() {
        assertThatThrownBy(() -> service.guardar(NUTRI, TipoArchivo.FOTO_PERFIL,
                new MockMultipartFile("foto", "x.pdf", "application/pdf", new byte[] {1, 2, 3})))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("JPG, PNG o WEBP");
    }

    @Test
    void la_matricula_si_acepta_pdf() {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.guardar(NUTRI, TipoArchivo.MATRICULA,
                new MockMultipartFile("m", "titulo.pdf", "application/pdf", "%PDF-1.4".getBytes()));

        assertThat(capturar().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void rechaza_content_type_no_permitido_aunque_la_extension_mienta() {
        assertThatThrownBy(() -> service.guardar(NUTRI, TipoArchivo.MATRICULA,
                new MockMultipartFile("m", "titulo.pdf", "application/x-msdownload", new byte[] {1})))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void tolera_el_charset_pegado_al_content_type() {
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.guardar(NUTRI, TipoArchivo.MATRICULA,
                new MockMultipartFile("m", "t.pdf", "application/pdf; charset=binary", "%PDF".getBytes()));

        assertThat(capturar().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void rechaza_archivo_vacio() {
        assertThatThrownBy(() -> service.guardar(NUTRI, TipoArchivo.MATRICULA,
                new MockMultipartFile("m", "v.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void rechaza_una_imagen_corrupta_con_mensaje_util() {
        assertThatThrownBy(() -> service.guardar(NUTRI, TipoArchivo.FOTO_PERFIL,
                new MockMultipartFile("foto", "rota.png", "image/png", "no soy una imagen".getBytes())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("imagen");
    }

    @Test
    void subir_de_nuevo_reemplaza_el_anterior() throws IOException {
        NutricionistaArchivo previo = new NutricionistaArchivo();
        previo.setNutricionistaId(NUTRI);
        previo.setTipo(TipoArchivo.FOTO_PERFIL);
        when(repo.findByNutricionistaIdAndTipoAndDeletedAtIsNull(NUTRI, TipoArchivo.FOTO_PERFIL))
                .thenReturn(Optional.of(previo));
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.guardar(NUTRI, TipoArchivo.FOTO_PERFIL,
                new MockMultipartFile("foto", "nueva.png", "image/png", png(100, 100)));

        // El anterior queda soft-deleted: el índice único es sobre los vivos.
        assertThat(previo.getDeletedAt()).isNotNull();
    }

    @Test
    void sin_foto_el_data_uri_es_null() {
        when(repo.findByNutricionistaIdAndTipoAndDeletedAtIsNull(NUTRI, TipoArchivo.FOTO_PERFIL))
                .thenReturn(Optional.empty());

        assertThat(service.fotoDataUri(NUTRI)).isNull();
    }
}
