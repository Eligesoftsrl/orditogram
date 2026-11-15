package com.eligesoft.orditogram.integrazione;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Credenziali per le chiamate API dei processi, configurate sul server (application.yml o variabili d'ambiente):
 *
 * app:
 *   credenziali:
 *     protocollo:
 *       tipo: header            # bearer | basic | header
 *       intestazione: X-API-Key
 *       valore: ${PROTOCOLLO_API_KEY}
 *
 * Nel BPMN l'analista indica solo il nome ("protocollo"): segreti e token non finiscono mai nel diagramma,
 * né nei pacchetti esportati.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class Credenziali {

    public static class Credenziale {
        private String tipo = "bearer";
        private String token;
        private String utente;
        private String password;
        private String intestazione;
        private String valore;
        private String descrizione;

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getUtente() { return utente; }
        public void setUtente(String utente) { this.utente = utente; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getIntestazione() { return intestazione; }
        public void setIntestazione(String intestazione) { this.intestazione = intestazione; }
        public String getValore() { return valore; }
        public void setValore(String valore) { this.valore = valore; }
        public String getDescrizione() { return descrizione; }
        public void setDescrizione(String descrizione) { this.descrizione = descrizione; }

        /** Aggiunge l'intestazione di autenticazione alla richiesta. */
        void applica(HttpRequest.Builder b) {
            switch (tipo == null ? "bearer" : tipo.toLowerCase(Locale.ROOT)) {
                case "basic" -> b.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((nvl(utente) + ":" + nvl(password)).getBytes(StandardCharsets.UTF_8)));
                case "header" -> {
                    if (intestazione == null || intestazione.isBlank())
                        throw new IllegalStateException("Credenziale di tipo header senza 'intestazione'");
                    b.header(intestazione, nvl(valore));
                }
                default -> b.header("Authorization", "Bearer " + nvl(token));
            }
        }

        private static String nvl(String s) { return s == null ? "" : s; }
    }

    private Map<String, Credenziale> credenziali = new LinkedHashMap<>();

    public Map<String, Credenziale> getCredenziali() { return credenziali; }
    public void setCredenziali(Map<String, Credenziale> credenziali) { this.credenziali = credenziali; }

    public Optional<Credenziale> trova(String nome) {
        return nome == null || nome.isBlank() ? Optional.empty() : Optional.ofNullable(credenziali.get(nome.trim()));
    }

    /** Solo nomi e tipi: i segreti non lasciano mai il server. */
    public List<Map<String, String>> elenco() {
        List<Map<String, String>> out = new ArrayList<>();
        credenziali.forEach((nome, c) -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("nome", nome);
            m.put("tipo", c.getTipo());
            m.put("descrizione", c.getDescrizione() != null ? c.getDescrizione() : "");
            out.add(m);
        });
        return out;
    }
}
