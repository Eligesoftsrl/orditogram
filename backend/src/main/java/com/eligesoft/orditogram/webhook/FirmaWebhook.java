package com.eligesoft.orditogram.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Firma degli avvisi (webhook): chi li riceve verifica che arrivino davvero da Orditogram e che nessuno li abbia
 * modificati o reinviati più tardi.
 *
 *   X-Orditogram-Firma: t=1759600000,v1=5f2c...   (HMAC-SHA256 di "t.corpo" con il segreto del webhook, in esadecimale)
 *
 * Il ricevente ricalcola l'HMAC su "<t>.<corpo ricevuto così com'è>", lo confronta a tempo costante
 * e rifiuta gli avvisi con t troppo vecchio (es. più di 5 minuti).
 */
public final class FirmaWebhook {

    public static final String INTESTAZIONE = "X-Orditogram-Firma";
    private static final SecureRandom CASO = new SecureRandom();

    /** Attese tra un tentativo e il successivo: dopo l'ultimo la consegna risulta fallita. */
    private static final Duration[] ATTESE = {
        Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2),
        Duration.ofHours(6), Duration.ofHours(12), Duration.ofHours(24)
    };
    public static final int MAX_TENTATIVI = ATTESE.length + 1;

    private FirmaWebhook() {}

    public static String firma(String segreto, long timestamp, String corpo) {
        return "t=" + timestamp + ",v1=" + hmac(segreto, timestamp + "." + corpo);
    }

    /** Verifica di una firma (la stessa che deve fare il ricevente): utile ai test e al ricevitore di prova. */
    public static boolean verifica(String segreto, String intestazione, String corpo, long ora, long tolleranzaSecondi) {
        if (segreto == null || intestazione == null || corpo == null) return false;
        Long t = null;
        String v1 = null;
        for (String parte : intestazione.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try { t = Long.parseLong(kv[1]); } catch (NumberFormatException e) { return false; }
            } else if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (t == null || v1 == null || Math.abs(ora - t) > tolleranzaSecondi) return false;
        return MessageDigest.isEqual(hmac(segreto, t + "." + corpo).getBytes(StandardCharsets.US_ASCII),
            v1.getBytes(StandardCharsets.US_ASCII));
    }

    /** Nuovo segreto casuale (256 bit). */
    public static String nuovoSegreto() {
        byte[] b = new byte[32];
        CASO.nextBytes(b);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /**
     * Quando riprovare dopo il tentativo numero n (1 = il primo invio), oppure null se non si riprova più.
     */
    public static Duration attesaDopo(int tentativo) {
        if (tentativo < 1 || tentativo >= MAX_TENTATIVI) return null;
        return ATTESE[tentativo - 1];
    }

    static String hmac(String segreto, String dati) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(dati.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC non disponibile", e);
        }
    }
}
