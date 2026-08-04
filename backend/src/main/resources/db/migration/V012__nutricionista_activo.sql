-- V012 — el admin puede desactivar (y reactivar) una nutricionista sin borrarla.
--
-- Hasta acá lo único que decidía si alguien podía entrar era `estado_validacion`, que describe la
-- validación de la solicitud (PENDIENTE/APROBADA/RECHAZADA) y es de una sola vía: no había forma de
-- dar de baja a una nutricionista aprobada sin mentirle al historial marcándola como RECHAZADA.
--
-- `activo` es otra cosa: espejo local del `enabled` del usuario en Keycloak, que es quien de verdad
-- decide el login. Se guarda acá para que la bandeja pueda mostrar el estado de las 20 filas de una
-- página sin hacerle 20 requests a Keycloak.

ALTER TABLE nutricionistas ADD COLUMN activo BOOLEAN NOT NULL DEFAULT false;

-- Backfill coherente con lo que hoy tiene Keycloak: aprobar es lo único que habilita el usuario.
UPDATE nutricionistas SET activo = (estado_validacion = 'APROBADA');

COMMENT ON COLUMN nutricionistas.activo IS
    'Espejo del enabled de Keycloak. false = no puede loguearse (pendiente, rechazada o dada de baja).';
