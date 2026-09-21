package com.bonosapp.modules.registro.controller;

import com.bonosapp.modules.registro.dto.RecuperarPasswordRequest;
import com.bonosapp.modules.registro.service.RecuperoPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-09 — "olvidé mi contraseña". Público y sin token: quien lo usa justamente no puede entrar.
 *
 * <p>Responde <b>204 siempre</b>, exista o no la cuenta. Un 404 acá convierte el endpoint en un
 * oráculo para averiguar qué mails están registrados en la plataforma.
 */
@RestController
@RequestMapping("/api/v1/password")
@RequiredArgsConstructor
public class RecuperoPasswordController {

    private final RecuperoPasswordService service;

    @PostMapping("/recuperar")
    public ResponseEntity<Void> recuperar(@Valid @RequestBody RecuperarPasswordRequest req) {
        service.enviarMail(req.email());
        return ResponseEntity.noContent().build();
    }
}
