package com.bonosapp.modules.profesion.controller;

import com.bonosapp.modules.profesion.dto.ProfesionResponse;
import com.bonosapp.modules.profesion.service.ProfesionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Público: lo consume el desplegable del registro, que se completa sin estar logueado. */
@RestController
@RequestMapping("/api/v1/profesiones")
@RequiredArgsConstructor
public class ProfesionController {

    private final ProfesionService service;

    @GetMapping
    public List<ProfesionResponse> listar() {
        return service.listar();
    }
}
