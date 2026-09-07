package com.bonosapp.modules.producto.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.bonosapp.integrations.contabilium.ContabiliumClient;
import com.bonosapp.integrations.contabilium.ContabiliumClient.CompanyInfo;
import com.bonosapp.integrations.contabilium.HttpContabiliumClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Probe READ-ONLY de Contabilium — SÓLO perfil dev. Primera-contacto controlada de Fase 2:
 * valida credenciales + params + shape real de una cuenta viva con SÓLO 2 requests
 * ({@code obtenerInfo} + página 1 de {@code buscarConceptos}) y SIN escribir en la DB
 * (a diferencia de {@code POST /admin/contabilium/sync-productos}, que hace full-scan + upsert).
 *
 * <p>Objetivo: confirmar contra la cuenta real (a) que las credenciales del {@code .env} obtienen
 * token, (b) que los params {@code filtro}/{@code page} devuelven datos, y (c) el shape real del
 * "concepto" — para eyeballear el mapeo Jackson ANTES de dejar que el sync escriba el catálogo.
 * Respeta el throttle del {@code HttpContabiliumClient} (15 req/10s). Nunca existe en prod
 * (bean no anotado fuera de dev). No expone secretos: sólo datos de negocio del catálogo.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/dev/contabilium")
@RequiredArgsConstructor
public class ContabiliumProbeController {

    private final ContabiliumClient contabiliumClient;

    @GetMapping("/probe")
    public Map<String, Object> probe() {
        CompanyInfo info = contabiliumClient.obtenerInfo();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("empresa", Map.of(
                "razonSocial", String.valueOf(info.razonSocial()),
                "cuit", String.valueOf(info.cuit())));

        if (contabiliumClient instanceof HttpContabiliumClient http) {
            // Raw: todos los campos que trae Contabilium (para analizar candidatos a filtro).
            JsonNode page = http.rawConceptos(1);
            JsonNode items = page != null ? page.path("Items") : null;
            if (items != null && items.isArray() && !items.isEmpty()) {
                List<String> keys = new ArrayList<>();
                items.get(0).fieldNames().forEachRemaining(keys::add);
                out.put("conceptoKeys", keys);
                List<JsonNode> sample = new ArrayList<>();
                for (int i = 0; i < Math.min(3, items.size()); i++) {
                    sample.add(items.get(i));
                }
                out.put("conceptoSample", sample);
            }
            // Rubros/categorías (candidato principal a filtro).
            out.put("rubros", http.rawRubros());
        } else {
            out.put("modo", "stub (no live) — sin raw");
        }
        return out;
    }
}
