package com.eligesoft.orditogram.messaggio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sistemi esterni autorizzati a inviare messaggi alle pratiche (POST /api/messaggi/{nome}).
 *
 * app:
 *   messaggi:
 *     chiavi:
 *       portale-cittadino: ${PORTALE_API_KEY}
 *       pec: ${PEC_API_KEY}
 *
 * Il sistema si autentica con l'intestazione X-API-Key; il suo nome finisce nello storico.
 */
@Component
@ConfigurationProperties(prefix = "app.messaggi")
public class MessaggiProperties {

    private Map<String, String> chiavi = new LinkedHashMap<>();

    public Map<String, String> getChiavi() { return chiavi; }
    public void setChiavi(Map<String, String> chiavi) { this.chiavi = chiavi; }

    /** Nome del sistema a cui appartiene la chiave, se valida. Confronto a tempo costante. */
    public Optional<String> sistema(String chiave) {
        if (chiave == null || chiave.isBlank()) return Optional.empty();
        byte[] data = chiave.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (var e : chiavi.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            if (java.security.MessageDigest.isEqual(data, e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                return Optional.of(e.getKey());
        }
        return Optional.empty();
    }
}
