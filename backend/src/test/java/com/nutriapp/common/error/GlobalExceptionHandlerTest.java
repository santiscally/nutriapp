package com.nutriapp.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        return req;
    }

    /**
     * Mandar JSON al {@code /registro} (que es multipart) caía en el catch-all y salía 500
     * "Error interno": un error del cliente reportado como falla del servidor, en un endpoint público.
     */
    @Test
    void contentTypeEquivocadoDa415YNo500() {
        ResponseEntity<ApiError> res = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_JSON, List.of(MediaType.MULTIPART_FORM_DATA)),
                request("/api/v1/registro"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().error()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(res.getBody().path()).isEqualTo("/api/v1/registro");
    }

    /** El mensaje dice qué se esperaba: sin eso, el cliente no sabe cómo corregir el request. */
    @Test
    void elMensajeDiceQueContentTypeSeEsperaba() {
        ResponseEntity<ApiError> res = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_JSON, List.of(MediaType.MULTIPART_FORM_DATA)),
                request("/api/v1/registro"));

        assertThat(res.getBody().message()).contains("multipart/form-data");
    }

    /** Sin tipos soportados declarados no se arma una frase colgada ("Se esperaba: ."). */
    @Test
    void sinTiposSoportadosNoQuedaUnaFraseCortada() {
        ResponseEntity<ApiError> res = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException("sin soportados"), request("/api/v1/x"));

        assertThat(res.getBody().message()).isEqualTo("Content-Type no soportado en esta ruta.");
    }
}
