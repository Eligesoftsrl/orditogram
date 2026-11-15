package com.eligesoft.orditogram.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra i segreti che Orditogram deve conservare e poi riusare (es. il segreto con cui firma i webhook),
 * con AES-256-GCM e una chiave che sta solo nella configurazione del server:
 *
 *   app.sicurezza.chiave-cifratura: ${ORDITOGRAM_CHIAVE_CIFRATURA}
 *
 * Senza chiave i segreti restano leggibili nel database (Orditogram lo segnala all'avvio).
 * Un valore già cifrato inizia con "enc:v1:"; i valori in chiaro salvati prima restano leggibili.
 */
@Component
public class Cifratura {

    private static final Logger log = LoggerFactory.getLogger(Cifratura.class);
    static final String PREFISSO = "enc:v1:";
    private static final SecureRandom CASO = new SecureRandom();

    private final byte[] chiave;

    public Cifratura(@Value("${app.sicurezza.chiave-cifratura:}") String frase) {
        this.chiave = frase == null || frase.isBlank() ? null : deriva(frase);
        if (chiave == null)
            log.warn("app.sicurezza.chiave-cifratura non impostata: i segreti dei webhook sono salvati in chiaro nel database");
    }

    public boolean attiva() { return chiave != null; }

    public String cifra(String testo) {
        if (testo == null || chiave == null) return testo;
        return cifra(chiave, testo);
    }

    public String decifra(String valore) {
        if (valore == null || !valore.startsWith(PREFISSO)) return valore;
        if (chiave == null) throw new IllegalStateException("Segreto cifrato ma app.sicurezza.chiave-cifratura non è impostata");
        return decifra(chiave, valore);
    }

    static byte[] deriva(String frase) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(frase.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String cifra(byte[] chiave, String testo) {
        try {
            byte[] iv = new byte[12];
            CASO.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(chiave, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(testo.getBytes(StandardCharsets.UTF_8));
            return PREFISSO + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Cifratura non riuscita", e);
        }
    }

    static String decifra(byte[] chiave, String valore) {
        try {
            byte[] tutto = Base64.getDecoder().decode(valore.substring(PREFISSO.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(chiave, "AES"), new GCMParameterSpec(128, tutto, 0, 12));
            return new String(c.doFinal(tutto, 12, tutto.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Segreto non decifrabile: la chiave di cifratura è cambiata?", e);
        }
    }
}
