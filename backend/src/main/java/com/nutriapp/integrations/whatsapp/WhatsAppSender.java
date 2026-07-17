package com.nutriapp.integrations.whatsapp;

/**
 * Port de envío de WhatsApp. Impl real (Fase 2): Meta Cloud API con template aprobado
 * (proveedor TBD con el cliente; fallback wa.me manual si el WABA se demora).
 */
public interface WhatsAppSender {

    void send(String toE164, String body);
}
