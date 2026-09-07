package com.bonosapp.modules.registro.dto;

import java.util.UUID;

/** Respuesta del alta pública: el nutricionista queda PENDIENTE de aprobación. */
public record RegistroResponse(
        UUID id,
        String estadoValidacion
) {}
