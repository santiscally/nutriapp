package com.nutriapp.modules.registro.controller;

import com.nutriapp.modules.registro.dto.RegistroRequest;
import com.nutriapp.modules.registro.dto.RegistroResponse;
import com.nutriapp.modules.registro.service.RegistroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoint público (sin JWT — ver SecurityConfig) de alta de nutricionista. */
@RestController
@RequestMapping("/api/v1/registro")
@RequiredArgsConstructor
public class RegistroController {

    private final RegistroService service;

    /**
     * Alta pública. <b>Multipart</b> (C-08): parte {@code datos} con el JSON del formulario y parte
     * {@code matricula} con el PDF o la foto del título. El archivo es opcional a nivel transporte
     * para no romper clientes viejos, pero el front lo pide obligatorio: el admin necesita algo que
     * mirar para validar la matrícula.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registrar(
            @Valid @RequestPart("datos") RegistroRequest req,
            @RequestPart(value = "matricula", required = false) MultipartFile matricula) {
        return service.registrar(req, matricula);
    }
}
