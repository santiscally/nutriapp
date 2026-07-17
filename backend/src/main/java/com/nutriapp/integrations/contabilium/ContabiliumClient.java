package com.nutriapp.integrations.contabilium;

/**
 * Port de Contabilium (ERP, solo lectura). Contrato según su colección Postman oficial
 * (ver instrucciones_claude/03-integraciones-apis.md §1).
 * Impl real (Fase 1.6/2): HttpContabiliumClient con token manager 24h + throttle 15 req/10s.
 */
public interface ContabiliumClient {

    /** GET /api/usuarios/obtenerinfo — health-check / validación de credenciales. */
    CompanyInfo obtenerInfo();

    record CompanyInfo(String razonSocial, String cuit) {}
}
