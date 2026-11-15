package com.eligesoft.orditogram.api;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Formato unico degli errori dell'API pubblica:
 *   { "errore": { "codice": "dati_non_validi", "messaggio": "...", "campi": [ { "campo": "...", "messaggio": "..." } ] } }
 * Il codice è stabile (i programmi lo possono confrontare), il messaggio è per le persone.
 */
public final class ErroriApi {

    private ErroriApi() {}

    /** Errore con codice, sollevato dai controller dell'API pubblica. */
    public static class ErroreApi extends RuntimeException {
        private final int stato;
        private final String codice;
        private final List<Map<String, String>> campi;

        public ErroreApi(int stato, String codice, String messaggio) {
            this(stato, codice, messaggio, List.of());
        }

        public ErroreApi(int stato, String codice, String messaggio, List<Map<String, String>> campi) {
            super(messaggio);
            this.stato = stato;
            this.codice = codice;
            this.campi = campi;
        }

        public int stato() { return stato; }
        public String codice() { return codice; }
        public List<Map<String, String>> campi() { return campi; }
    }

    public static ErroreApi nonTrovata(String cosa) {
        return new ErroreApi(404, "non_trovato", cosa + " non trovato o non accessibile con questa chiave");
    }

    public static ErroreApi permesso(String ambito) {
        return new ErroreApi(403, "permesso_mancante", "La chiave non ha il permesso \"" + ambito + "\"");
    }

    public static ErroreApi richiesta(String messaggio) {
        return new ErroreApi(400, "richiesta_non_valida", messaggio);
    }

    static void scrivi(HttpServletResponse res, int stato, String codice, String messaggio) throws IOException {
        res.setStatus(stato);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getOutputStream().write(("{\"errore\":{\"codice\":\"" + codice + "\",\"messaggio\":\"" + json(messaggio) + "\"}}")
            .getBytes(StandardCharsets.UTF_8));
    }

    static String json(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.toString();
    }
}
