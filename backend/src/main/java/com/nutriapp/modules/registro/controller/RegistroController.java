package com.nutriapp.modules.registro.controller;

import com.nutriapp.modules.registro.dto.RegistroRequest;
import com.nutriapp.modules.registro.dto.RegistroResponse;
import com.nutriapp.modules.registro.service.RegistroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público (sin JWT — ver SecurityConfig) de alta de nutricionista. */
@RestController
@RequestMapping("/api/v1/registro")
@RequiredArgsConstructor
public class RegistroController {

    private final RegistroService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registrar(@Valid @RequestBody RegistroRequest req) {
        return service.registrar(req);
    }
}
