package com.nutriapp.modules.producto.controller;

import com.nutriapp.integrations.contabilium.ContabiliumClient;
import com.nutriapp.integrations.contabilium.ContabiliumClient.CompanyInfo;
import com.nutriapp.integrations.contabilium.ContabiliumClient.Concepto;
import com.nutriapp.integrations.contabilium.ContabiliumClient.ConceptoPage;
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
        ConceptoPage pagina = contabiliumClient.buscarConceptos("", 1);
        List<Map<String, Object>> muestra = pagina.items().stream()
                .limit(5)
                .map(this::resumen)
                .toList();
        return Map.of(
                "empresa", Map.of(
                        "razonSocial", String.valueOf(info.razonSocial()),
                        "cuit", String.valueOf(info.cuit())),
                "totalItems", pagina.totalItems(),
                "totalPage", pagina.totalPage(),
                "itemsEnPagina1", pagina.items().size(),
                "muestra", muestra);
    }

    /** LinkedHashMap (no Map.of): tolera valores null si algún campo del concepto no vino. */
    private Map<String, Object> resumen(Concepto c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("tipo", c.tipo());
        m.put("nombre", c.nombre());
        m.put("codigo", c.codigo());
        m.put("estado", c.estado());
        m.put("precio", c.precio());
        m.put("precioFinal", c.precioFinal());
        m.put("stock", c.stock());
        return m;
    }
}
