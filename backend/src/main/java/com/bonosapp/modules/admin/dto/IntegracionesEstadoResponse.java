package com.bonosapp.modules.admin.dto;

import java.util.List;

/** Envelope del estado de todas las integraciones externas (2.7). */
public record IntegracionesEstadoResponse(List<IntegracionEstadoResponse> integraciones) {}
