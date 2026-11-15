package com.eligesoft.orditogram.integrazione;

import com.eligesoft.orditogram.connettore.Testi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La parte "pura" del connettore API: prepara la richiesta dai parametri scritti dall'analista,
 * la esegue e legge i valori dalla risposta. Non dipende da Spring né da Activiti, così si prova da sola.
 */
public final class ClienteApi {

    public static final Set<String> METODI = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    /** Percorsi speciali della mappatura: codice HTTP e corpo intero della risposta. */
    public static final String PERCORSO_STATO = "@stato";
    public static final String PERCORSO_CORPO = "@corpo";

    private static final Pattern NOME_MAPPATURA = Pattern.compile("[a-zA-Z][\\w-]*");
    private static final Pattern SEGMENTO = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)]");
    private static final int MAX_CORPO = 2 * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private ClienteApi() {}

    public record Richiesta(String metodo, String url, Map<String, String> intestazioni, String corpo, int timeoutSecondi) {}

    public record Risposta(int stato, String corpo) {
        public boolean ok() { return stato >= 200 && stato < 300; }
    }

    /** Errore della chiamata, con un messaggio comprensibile per lo storico. */
    public static class ErroreApi extends RuntimeException {
        private final int stato;
        public ErroreApi(String messaggio, int stato, Throwable causa) {
            super(messaggio, causa);
            this.stato = stato;
        }
        public int stato() { return stato; }
    }

    // ------------------------------------------------------------ preparazione

    /**
     * Sostituisce i ${dati}: nell'URL i valori sono codificati, nel corpo JSON resi sicuri per le stringhe,
     * nelle intestazioni ripuliti dagli a capo.
     */
    public static Richiesta prepara(Map<String, String> p, Map<String, Object> dati) {
        String metodo = p.getOrDefault("metodo", "GET").trim().toUpperCase(Locale.ROOT);
        if (!METODI.contains(metodo)) throw new ErroreApi("Metodo HTTP non valido: " + metodo, 0, null);

        String urlTemplate = p.getOrDefault("url", "").trim();
        String url = Testi.sostituisci(urlTemplate, dati, ClienteApi::encodeUrl);
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            throw new ErroreApi("Indirizzo non valido (deve iniziare con http:// o https://): " + url, 0, null);

        Map<String, String> intestazioni = new LinkedHashMap<>();
        for (String riga : p.getOrDefault("intestazioni", "").split("\\R")) {
            int i = riga.indexOf(':');
            if (riga.isBlank() || i <= 0) continue;
            String nome = riga.substring(0, i).trim();
            String valore = Testi.sostituisci(riga.substring(i + 1).trim(), dati, v -> v.replaceAll("[\\r\\n]+", " "));
            intestazioni.put(nome, valore);
        }

        String corpo = null;
        String corpoTemplate = p.getOrDefault("corpo", "");
        if (!corpoTemplate.isBlank() && !metodo.equals("GET") && !metodo.equals("DELETE")) {
            boolean json = tipoContenuto(intestazioni).map(t -> t.contains("json")).orElse(true);
            corpo = Testi.sostituisci(corpoTemplate, dati, json ? Testi::escapeJson : v -> v);
            if (tipoContenuto(intestazioni).isEmpty()) intestazioni.put("Content-Type", "application/json; charset=UTF-8");
        }
        intestazioni.putIfAbsent("Accept", "application/json");

        int timeout = 30;
        try {
            timeout = Math.max(1, Math.min(300, Integer.parseInt(p.getOrDefault("timeout", "30").trim())));
        } catch (NumberFormatException ignored) {
            // resta il predefinito
        }
        return new Richiesta(metodo, url, intestazioni, corpo, timeout);
    }

    private static Optional<String> tipoContenuto(Map<String, String> h) {
        return h.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("Content-Type"))
            .map(e -> e.getValue().toLowerCase(Locale.ROOT)).findFirst();
    }

    static String encodeUrl(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ------------------------------------------------------------ esecuzione

    public static Risposta esegui(Richiesta r, Credenziali.Credenziale credenziale) {
        HttpRequest.Builder b;
        try {
            b = HttpRequest.newBuilder(URI.create(r.url())).timeout(Duration.ofSeconds(r.timeoutSecondi()));
        } catch (IllegalArgumentException e) {
            throw new ErroreApi("Indirizzo non valido: " + r.url(), 0, e);
        }
        try {
            r.intestazioni().forEach(b::header);
            if (credenziale != null) credenziale.applica(b);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ErroreApi("Intestazione non valida: " + e.getMessage(), 0, e);
        }
        HttpRequest.BodyPublisher body = r.corpo() == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(r.corpo(), StandardCharsets.UTF_8);
        b.method(r.metodo(), body);
        try {
            HttpResponse<byte[]> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = res.body() == null ? new byte[0] : res.body();
            String testo = new String(bytes, 0, Math.min(bytes.length, MAX_CORPO), StandardCharsets.UTF_8);
            return new Risposta(res.statusCode(), testo);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ErroreApi("Il servizio non ha risposto entro " + r.timeoutSecondi() + " secondi", 0, e);
        } catch (java.net.ConnectException e) {
            throw new ErroreApi("Servizio non raggiungibile: " + host(r.url()), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErroreApi("Chiamata interrotta", 0, e);
        } catch (Exception e) {
            throw new ErroreApi("Chiamata non riuscita: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), 0, e);
        }
    }

    static String host(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return url; }
    }

    // ------------------------------------------------------------ risposta

    /** Nomi dei dati scritti dalla mappatura (parte sinistra delle righe "dato = percorso"). */
    public static Set<String> datiDaMappatura(String mappatura) {
        Set<String> out = new LinkedHashSet<>();
        for (String[] r : righe(mappatura)) out.add(r[0]);
        return out;
    }

    public static List<String[]> righe(String mappatura) {
        List<String[]> out = new ArrayList<>();
        if (mappatura == null) return out;
        // "nome = valore": diviso al primo "=", senza espressioni con ripetizioni annidate (tempo lineare)
        for (String riga : mappatura.split("\\R")) {
            int uguale = riga.indexOf('=');
            if (uguale < 0) continue;
            String nome = riga.substring(0, uguale).strip(), valore = riga.substring(uguale + 1).strip();
            if (!valore.isEmpty() && NOME_MAPPATURA.matcher(nome).matches()) out.add(new String[]{nome, valore});
        }
        return out;
    }

    /**
     * Legge i valori dalla risposta. Percorsi: "numero", "esito.codice", "elenco[0].id",
     * "@stato" (codice HTTP), "@corpo" (risposta intera). Tutti i valori diventano testo.
     */
    public static Map<String, String> estrai(String mappatura, Risposta r, ObjectMapper mapper) {
        JsonNode json = null;
        try {
            if (r.corpo() != null && !r.corpo().isBlank()) json = mapper.readTree(r.corpo());
        } catch (Exception ignored) {
            // risposta non JSON: si possono usare solo @stato e @corpo
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String[] riga : righe(mappatura)) {
            String percorso = riga[1];
            String valore;
            if (percorso.equals(PERCORSO_STATO)) valore = String.valueOf(r.stato());
            else if (percorso.equals(PERCORSO_CORPO)) valore = r.corpo();
            else valore = testo(leggi(json, percorso));
            out.put(riga[0], valore);
        }
        return out;
    }

    public static JsonNode leggi(JsonNode nodo, String percorso) {
        if (nodo == null) return null;
        String p = percorso.startsWith("$.") ? percorso.substring(2) : percorso;
        Matcher m = SEGMENTO.matcher(p);
        JsonNode cur = nodo;
        while (m.find() && cur != null) {
            if (m.group(1) != null) {
                String k = m.group(1).trim();
                cur = cur.isArray() && k.matches("\\d+") ? cur.get(Integer.parseInt(k)) : cur.get(k);
            } else {
                cur = cur.get(Integer.parseInt(m.group(2)));
            }
        }
        return cur;
    }

    static String testo(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return "";
        if (n.isValueNode()) return n.asText();
        return n.toString();
    }

    /** Breve estratto della risposta per lo storico. */
    public static String estratto(String corpo) {
        if (corpo == null) return "";
        String s = corpo.replaceAll("\\s+", " ").trim();
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
