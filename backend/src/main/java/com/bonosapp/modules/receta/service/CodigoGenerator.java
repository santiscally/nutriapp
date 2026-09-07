package com.bonosapp.modules.receta.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Genera el código único de receta (= código de cupón TiendaNube). Formato RX-XXXXXX,
 * alfabeto sin caracteres ambiguos (sin O/0/I/1) para que sea legible en el mail/WhatsApp.
 */
@Component
public class CodigoGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    public String generar() {
        StringBuilder sb = new StringBuilder("RX-");
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
