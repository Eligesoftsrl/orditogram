package com.eligesoft.orditogram.anagrafica;

import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.registro.RegistroService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Importazione da CSV ed esportazione delle schede di un tipo.
 *
 * Importazione (regola "modifica"): prima riga con i nomi dei dati o le etichette dei campi, separatore ";" o ",",
 * UTF-8, valori tra virgolette se contengono il separatore. Se il tipo ha un campo chiave e la riga ne ha il valore,
 * la scheda con quel valore si aggiorna (solo le colonne presenti), altrimenti se ne crea una nuova. Ogni riga passa
 * dalle stesse regole delle schede inserite a mano (SchedeService) e si salva da sola: una riga sbagliata si scarta
 * con il motivo, le altre restano. Tabelle e campi in sola lettura non si importano.
 *
 * Esportazione (regola "esporta"): tutte le schede del tipo (al massimo MAX_ESPORTA), senza i campi riservati
 * all'utente; il file lo prepara l'interfaccia. Le esportazioni vanno nel registro.
 */
@Service
public class ImportaEsportaSchede {

    static final int MAX_RIGHE = 5000;
    static final int MAX_ESPORTA = 20_000;
    static final int MAX_BYTE = 10 * 1024 * 1024;

    /** ignorate: colonne del file che non si importano (tabelle, campi in sola lettura, nomi delle schede collegate). */
    public record EsitoImport(int create, int aggiornate, int invariate, List<String> scartate, List<String> ignorate) { }

    /** Suffisso della colonna con il nome della scheda collegata, che l'esportazione affianca all'id. */
    public static final String SUFFISSO_NOME = " (nome)";

    public record Esportazione(List<Map<String, String>> colonne, List<Map<String, Object>> righe) { }

    private final SchedeService schede;
    private final RegistroService registro;

    public ImportaEsportaSchede(SchedeService schede, RegistroService registro) {
        this.schede = schede;
        this.registro = registro;
    }

    // ------------------------------------------------------------ importazione

    public EsitoImport importa(String tipo, byte[] contenuto, String nomeFile) {
        if (contenuto.length > MAX_BYTE) throw bad("Il file è troppo grande (al massimo 10 MB)");
        SchedeService.TipoVisibile t = schede.tipo(tipo);
        if (!t.azioni().contains(PermessoAnagrafica.MODIFICA))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non hai il permesso di modificare queste schede");
        String testo = new String(contenuto, StandardCharsets.UTF_8).replace("﻿", "");
        List<List<String>> righe = leggiCsv(testo);
        if (righe.isEmpty()) throw bad("Il file è vuoto");
        if (righe.size() > MAX_RIGHE + 1) throw bad("Il file ha troppe righe (al massimo " + MAX_RIGHE + ")");

        // colonne: nome del dato o etichetta del campo
        Map<String, JsonNode> campi = new LinkedHashMap<>();
        Map<String, String> perEtichetta = new HashMap<>();
        for (JsonNode f : t.definizione().path("fields")) {
            if (ValoriForm.SEZIONE.equals(f.path("type").asText())) continue;
            campi.put(f.path("name").asText(), f);
            perEtichetta.put(f.path("label").asText("").trim().toLowerCase(Locale.ROOT), f.path("name").asText());
        }
        // una colonna null si salta: così il file esportato si può reimportare così com'è
        List<String> intestazione = new ArrayList<>();
        List<String> ignorate = new ArrayList<>();
        for (String h : righe.get(0)) {
            String c = h.trim();
            String nome = nomeColonna(campi, perEtichetta, c);
            if (nome == null && c.endsWith(SUFFISSO_NOME)) {
                String rel = nomeColonna(campi, perEtichetta, c.substring(0, c.length() - SUFFISSO_NOME.length()).trim());
                if (rel != null && ValoriForm.ANAGRAFICA.equals(campi.get(rel).path("type").asText())) {
                    ignorate.add(c); intestazione.add(null); continue;
                }
            }
            if (nome == null) throw bad("Colonna \"" + c + "\" sconosciuta: usa i nomi dei dati o le etichette dei campi");
            JsonNode f = campi.get(nome);
            if (ValoriForm.TABELLA.equals(f.path("type").asText()) || f.path("readonly").asBoolean(false)) {
                ignorate.add(c); intestazione.add(null); continue;
            }
            if (intestazione.contains(nome)) throw bad("Colonna \"" + c + "\" ripetuta");
            intestazione.add(nome);
        }
        if (intestazione.stream().allMatch(Objects::isNull)) throw bad("Nessuna colonna da importare");
        String chiave = campi.values().stream().filter(f -> f.path("chiave").asBoolean(false))
            .map(f -> f.path("name").asText()).filter(intestazione::contains).findFirst().orElse(null);

        int create = 0, aggiornate = 0, invariate = 0;
        List<String> scartate = new ArrayList<>();
        Set<String> chiaviViste = new HashSet<>();
        for (int i = 1; i < righe.size(); i++) {
            List<String> r = righe.get(i);
            if (r.stream().allMatch(String::isBlank)) continue;
            String n = "Riga " + (i + 1) + ": ";
            if (r.size() > intestazione.size()) { scartate.add(n + "più valori che colonne"); continue; }
            Map<String, Object> dati = new LinkedHashMap<>();
            for (int k = 0; k < intestazione.size(); k++) {
                if (intestazione.get(k) == null) continue;
                String v = k < r.size() ? r.get(k).trim() : "";
                dati.put(intestazione.get(k), valore(campi.get(intestazione.get(k)), v));
            }
            try {
                String valoreChiave = chiave == null ? null : DefinizioneAnagrafica.normalizza(dati.get(chiave));
                if (valoreChiave != null && !chiaviViste.add(valoreChiave)) { scartate.add(n + "chiave ripetuta nel file"); continue; }
                SchedeService.Riga esistente = valoreChiave == null ? null
                    : schede.cerca(tipo, null, chiave, valoreChiave, "TUTTE", 0, 1).schede().stream().findFirst().orElse(null);
                if (esistente == null) {
                    dati.values().removeIf(v -> v == null || "".equals(v));
                    schede.crea(tipo, dati);
                    create++;
                } else {
                    SchedeService.Scheda s = schede.leggi(tipo, esistente.id());
                    Map<String, Object> unione = new LinkedHashMap<>(s.dati());
                    dati.forEach((k, v) -> { if (v == null || "".equals(v)) unione.remove(k); else unione.put(k, v); });
                    SchedeService.Scheda dopo = schede.modifica(tipo, s.id(), s.versione(), unione, "Importazione da " + nomeFile);
                    if (dopo.versione() == s.versione()) invariate++; else aggiornate++;
                }
            } catch (DatiNonValidi e) {
                scartate.add(n + String.join("; ", e.errori().stream().map(x -> etichetta(campi, x.campo()) + ": " + x.messaggio()).toList()));
            } catch (ResponseStatusException e) {
                scartate.add(n + e.getReason());
            }
            if (scartate.size() > 200) { scartate.add("Troppe righe scartate: importazione interrotta"); break; }
        }
        registro.registra("ANAGRAFICA_IMPORTATA", tipo, Objects.toString(nomeFile, "file CSV") + ": " + create + " create, "
            + aggiornate + " aggiornate, " + invariate + " invariate, " + scartate.size() + " righe scartate");
        return new EsitoImport(create, aggiornate, invariate, scartate, ignorate);
    }

    /** Il valore di una cella come lo vuole il campo: sì/no per le caselle, date anche come GG/MM/AAAA. */
    static Object valore(JsonNode f, String v) {
        if (v.isEmpty()) return "";
        // l'esportazione mette un apostrofo davanti ai testi che Excel prenderebbe per formule: qui si toglie
        if (v.length() > 1 && v.charAt(0) == '\'' && "=+-@\t\r".indexOf(v.charAt(1)) >= 0) v = v.substring(1);
        switch (f.path("type").asText("text")) {
            case "checkbox" -> {
                String s = v.toLowerCase(Locale.ROOT);
                if (Set.of("sì", "si", "true", "1", "x", "vero").contains(s)) return true;
                if (Set.of("no", "false", "0", "falso").contains(s)) return false;
                return v;
            }
            case "date" -> {
                if (v.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                    String[] p = v.split("/");
                    return String.format("%s-%02d-%02d", p[2], Integer.parseInt(p[1]), Integer.parseInt(p[0]));
                }
                return v;
            }
            case "number" -> {
                // 1.234,56 → 1234.56; 1234,5 → 1234.5
                if (v.matches("-?[\\d.]*,\\d+")) return v.replace(".", "").replace(',', '.');
                return v;
            }
            default -> { return v; }
        }
    }

    /**
     * Righe di un CSV: separatore ";" o "," (quello della prima riga), valori tra virgolette con "" per le virgolette.
     * Lineare sulla lunghezza del testo.
     */
    static List<List<String>> leggiCsv(String testo) {
        int fine = testo.indexOf('\n');
        String prima = fine < 0 ? testo : testo.substring(0, fine);
        char sep = prima.indexOf(';') >= 0 ? ';' : ',';
        List<List<String>> out = new ArrayList<>();
        List<String> riga = new ArrayList<>();
        StringBuilder cella = new StringBuilder();
        boolean virgolette = false;
        for (int i = 0; i < testo.length(); i++) {
            char c = testo.charAt(i);
            if (virgolette) {
                if (c == '"' && i + 1 < testo.length() && testo.charAt(i + 1) == '"') { cella.append('"'); i++; }
                else if (c == '"') virgolette = false;
                else cella.append(c);
            } else if (c == '"' && cella.isEmpty()) {
                virgolette = true;
            } else if (c == sep) {
                riga.add(cella.toString()); cella.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < testo.length() && testo.charAt(i + 1) == '\n') i++;
                riga.add(cella.toString()); cella.setLength(0);
                out.add(riga); riga = new ArrayList<>();
                if (out.size() > MAX_RIGHE + 2) return out;
            } else {
                cella.append(c);
            }
        }
        if (!cella.isEmpty() || !riga.isEmpty()) { riga.add(cella.toString()); out.add(riga); }
        return out;
    }

    // ------------------------------------------------------------ esportazione

    public Esportazione esporta(String tipo) {
        SchedeService.TipoVisibile t = schede.tipo(tipo);
        if (!t.azioni().contains(PermessoAnagrafica.ESPORTA))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non hai il permesso di esportare queste schede");
        List<Map<String, String>> colonne = new ArrayList<>();
        for (JsonNode f : t.definizione().path("fields"))
            if (!ValoriForm.SEZIONE.equals(f.path("type").asText()))
                colonne.add(Map.of("nome", f.path("name").asText(), "etichetta", f.path("label").asText(f.path("name").asText()),
                    "tipo", f.path("type").asText("text")));
        List<Map<String, Object>> righe = schede.perEsportazione(tipo, MAX_ESPORTA);
        registro.registra("ANAGRAFICA_ESPORTATA", tipo, righe.size() + " schede");
        return new Esportazione(colonne, righe);
    }

    /** Il nome del dato di una colonna: il nome stesso o l'etichetta del campo (maiuscole a parte). */
    private static String nomeColonna(Map<String, JsonNode> campi, Map<String, String> perEtichetta, String colonna) {
        return campi.containsKey(colonna) ? colonna : perEtichetta.get(colonna.toLowerCase(Locale.ROOT));
    }

    private static String etichetta(Map<String, JsonNode> campi, String nome) {
        JsonNode f = campi.get(nome);
        return f == null ? nome : f.path("label").asText(nome);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
