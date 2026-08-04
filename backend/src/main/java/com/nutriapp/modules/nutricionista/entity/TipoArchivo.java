package com.nutriapp.modules.nutricionista.entity;

/** Tipos de archivo que puede tener una nutricionista. Uno vigente de cada tipo. */
public enum TipoArchivo {
    /** C-08: matrícula o título que sube al registrarse; lo mira el admin para validarla. */
    MATRICULA,
    /** C-17: foto de perfil (el circulito del navbar). Se guarda ya redimensionada. */
    FOTO_PERFIL
}
