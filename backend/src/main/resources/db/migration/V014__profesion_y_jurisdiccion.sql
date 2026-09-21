-- S-11: profesion y jurisdiccion dejan de viajar concatenadas dentro de `matricula`.

ALTER TABLE nutricionistas ADD COLUMN profesion VARCHAR(120);
ALTER TABLE nutricionistas ADD COLUMN jurisdiccion_matricula VARCHAR(80);

-- Las altas previas se dejan como están: no hay forma segura de partir "CABA · N° 1234".
COMMENT ON COLUMN nutricionistas.profesion IS
    'Profesión declarada en el registro (valor de la tabla profesiones). NULL en altas previas a V014.';
COMMENT ON COLUMN nutricionistas.jurisdiccion_matricula IS
    'Provincia o CABA de la matrícula. NULL en altas previas a V014: ahí va adentro de matricula.';

-- En DB y no en el front: la lista la manda el cliente por Excel y cambia sin que se despliegue nada.
CREATE TABLE profesiones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre      VARCHAR(120) NOT NULL UNIQUE,
    activo      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  UUID,
    deleted_at  TIMESTAMPTZ
);
CREATE TRIGGER trg_profesiones_updated BEFORE UPDATE ON profesiones
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

INSERT INTO profesiones (nombre) VALUES
    ('Acompañante terapéutico'),
    ('Agente de propaganda médica'),
    ('Agente sanitario'),
    ('Asistente dental'),
    ('Auxiliar de anestesia'),
    ('Auxiliar de cirugía'),
    ('Auxiliar de enfermería'),
    ('Auxiliar de estadística'),
    ('Auxiliar de esterilización'),
    ('Auxiliar de farmacia'),
    ('Auxiliar de hemoterapia'),
    ('Auxiliar de laboratorio'),
    ('Auxiliar de radiología'),
    ('Auxiliar sanitario'),
    ('Bacteriólogo'),
    ('Bioquímico'),
    ('Biólogo'),
    ('Cosmetólogo'),
    ('Educador sanitario'),
    ('Enfermero'),
    ('Farmacéutico'),
    ('Fonoaudiólogo'),
    ('Genetista'),
    ('Instrumentador quirúrgico'),
    ('Kinesiólogo'),
    ('Licenciado en acompañamiento terapéutico'),
    ('Licenciado en enfermería'),
    ('Licenciado en instrumentación quirúrgica'),
    ('Licenciado en obstetricia'),
    ('Licenciado en podología'),
    ('Licenciado en producción de bioimágenes'),
    ('Licenciado en prótesis y órtesis'),
    ('Microbiólogo'),
    ('Musicoterapeuta'),
    ('Médico'),
    ('Médico veterinario'),
    ('Nutricionista'),
    ('Odontólogo'),
    ('Otros'),
    ('Podólogo'),
    ('Profesional de análisis clínicos'),
    ('Profesor de educación especial'),
    ('Psicomotricista'),
    ('Psicopedagogo'),
    ('Psicólogo'),
    ('Saneamiento ambiental'),
    ('Tecnólogo en alimentos'),
    ('Terapista ocupacional'),
    ('Trabajador social'),
    ('Técnico en administración de servicios de salud'),
    ('Técnico en alimentos'),
    ('Técnico en anestesia'),
    ('Técnico en citología'),
    ('Técnico en diálisis'),
    ('Técnico en emergencias médicas'),
    ('Técnico en estadística'),
    ('Técnico en esterilización'),
    ('Técnico en farmacia'),
    ('Técnico en fonoaudiología'),
    ('Técnico en hematología'),
    ('Técnico en hemoterapia'),
    ('Técnico en histología'),
    ('Técnico en laboratorio'),
    ('Técnico en nutrición'),
    ('Técnico en obstetricia'),
    ('Técnico en prácticas cardiológicas'),
    ('Técnico en prótesis dental'),
    ('Técnico en prótesis y órtesis'),
    ('Técnico en psicomotricidad'),
    ('Técnico en psicopedagogía'),
    ('Técnico en radiología'),
    ('Técnico en rehabilitación'),
    ('Técnico en saneamiento ambiental'),
    ('Técnico en terapia ocupacional'),
    ('Técnico en trabajo social'),
    ('Técnico en óptica');
