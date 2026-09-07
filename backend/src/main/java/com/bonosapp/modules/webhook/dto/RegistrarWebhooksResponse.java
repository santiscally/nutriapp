package com.bonosapp.modules.webhook.dto;

import java.util.List;

/** Resultado de suscribir la app a los eventos de la tienda. Se puede repetir sin duplicar nada. */
public record RegistrarWebhooksResponse(String url, List<String> creados, List<String> yaEstaban) {}
