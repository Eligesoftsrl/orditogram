package com.eligesoft.orditogram.glossario;

import com.eligesoft.orditogram.registro.RegistroService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Il glossario dell'installazione: per ogni {@link Termine} la parola scelta o quella predefinita.
 * Lo legge chiunque (anche la pagina di accesso, da /api/info); lo cambia solo l'amministratore.
 */
@Service
public class GlossarioService {

    /** Lettere (anche accentate), spazi, apostrofi, trattini e punti: niente markup né caratteri di controllo. */
    private static final Pattern PAROLA = Pattern.compile("[\\p{L}][\\p{L}\\p{M} '’.\\-]{0,59}");

    public record Parola(String chiave, String nome, String aiuto, String singolare, String plurale, String genere,
                         boolean predefinito, String singolarePredefinito, String pluralePredefinito,
                         String generePredefinito) { }

    private final TermineGlossarioRepository repo;
    private final RegistroService registro;

    public GlossarioService(TermineGlossarioRepository repo, RegistroService registro) {
        this.repo = repo;
        this.registro = registro;
    }

    /** Tutti i termini, nell'ordine del catalogo, con la parola in uso. */
    @Transactional(readOnly = true)
    public List<Parola> termini() {
        Map<String, TermineGlossario> scelti = new HashMap<>();
        repo.findAll().forEach(t -> scelti.put(t.getChiave(), t));
        List<Parola> out = new ArrayList<>();
        for (Termine t : Termine.values()) {
            TermineGlossario s = scelti.get(t.chiave());
            out.add(new Parola(t.chiave(), t.nome(), t.aiuto(),
                s != null ? s.getSingolare() : t.singolare(),
                s != null ? s.getPlurale() : t.plurale(),
                s != null ? s.getGenere() : t.genere(),
                s == null, t.singolare(), t.plurale(), t.genere()));
        }
        return out;
    }

    /** Solo le parole, per l'interfaccia: {"ente": {"singolare", "plurale", "genere"}, ...}. */
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> parole() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Parola p : termini())
            out.put(p.chiave(), Map.of("singolare", p.singolare(), "plurale", p.plurale(), "genere", p.genere()));
        return out;
    }

    /** La parola al singolare, es. per i testi del server ("Ufficio" visto dagli utenti esterni). */
    @Transactional(readOnly = true)
    public String singolare(Termine t) {
        return repo.findById(t.chiave()).map(TermineGlossario::getSingolare).orElse(t.singolare());
    }

    /** Prima lettera maiuscola. */
    public static String maiuscola(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * La parola con l'articolo, per i testi del server (stesse regole di lib/glossario.js):
     * "il" → "la pratica" / "il ticket" / "l'istanza", "del" → "della pratica" / "del ticket".
     */
    @Transactional(readOnly = true)
    public String conArticolo(Termine t, boolean di) {
        TermineGlossario s = repo.findById(t.chiave()).orElse(null);
        String parola = s != null ? s.getSingolare() : t.singolare();
        boolean f = "F".equals(s != null ? s.getGenere() : t.genere());
        boolean vocale = parola.matches("(?iu)^[aeiouàèéìòóùh].*") && !parola.matches("(?iu)^i[aeiou].*");
        boolean impura = parola.matches("(?iu)^(s[^aeiouàèéìòóù]|z|gn|ps|pn|x|y|i[aeiou]).*");
        String art = vocale ? "l'" : f ? "la" : impura ? "lo" : "il";
        if (di) art = switch (art) { case "l'" -> "dell'"; case "la" -> "della"; case "lo" -> "dello"; default -> "del"; };
        return art.endsWith("'") ? art + parola : art + " " + parola;
    }

    /** Desinenza di un aggettivo in -o/-a accordato al termine: radice "trovat" → "trovata" / "trovato". */
    @Transactional(readOnly = true)
    public String accorda(Termine t, String radice) {
        String genere = repo.findById(t.chiave()).map(TermineGlossario::getGenere).orElse(t.genere());
        return radice + ("F".equals(genere) ? "a" : "o");
    }

    /**
     * Salva le parole indicate; un termine uguale al predefinito (o senza parole) torna predefinito.
     * Termini sconosciuti o parole non valide: 400, e non si salva niente.
     */
    @Transactional
    public List<Parola> salva(Map<String, Map<String, String>> nuove) {
        if (nuove == null) throw bad("Indica i termini da salvare");
        List<String> cambiati = new ArrayList<>();
        for (var voce : nuove.entrySet()) {
            Termine t = Termine.da(voce.getKey()).orElseThrow(() -> bad("Termine sconosciuto: " + voce.getKey()));
            Map<String, String> v = voce.getValue() != null ? voce.getValue() : Map.of();
            String singolare = pulisci(v.get("singolare"));
            String plurale = pulisci(v.get("plurale"));
            String genere = v.get("genere") == null ? t.genere() : v.get("genere").trim().toUpperCase(Locale.ROOT);
            if (singolare.isEmpty() && plurale.isEmpty()) {
                if (repo.existsById(t.chiave())) { repo.deleteById(t.chiave()); cambiati.add(t.chiave() + " = predefinito"); }
                continue;
            }
            if (!PAROLA.matcher(singolare).matches() || !PAROLA.matcher(plurale).matches())
                throw bad("\"" + t.nome() + "\": singolare e plurale da 1 a 60 caratteri, solo lettere, spazi, apostrofi e trattini");
            if (!genere.equals("M") && !genere.equals("F"))
                throw bad("\"" + t.nome() + "\": il genere è M (maschile) o F (femminile)");
            if (singolare.equals(t.singolare()) && plurale.equals(t.plurale()) && genere.equals(t.genere())) {
                if (repo.existsById(t.chiave())) { repo.deleteById(t.chiave()); cambiati.add(t.chiave() + " = predefinito"); }
                continue;
            }
            TermineGlossario prima = repo.findById(t.chiave()).orElse(null);
            if (prima != null && prima.getSingolare().equals(singolare) && prima.getPlurale().equals(plurale)
                && prima.getGenere().equals(genere)) continue;
            repo.save(new TermineGlossario(t.chiave(), singolare, plurale, genere));
            cambiati.add(t.chiave() + " = " + singolare + "/" + plurale + " (" + genere + ")");
        }
        if (!cambiati.isEmpty()) registro.registra("GLOSSARIO_MODIFICATO", "glossario", String.join("; ", cambiati));
        return termini();
    }

    /**
     * Parole portate da un pacchetto (glossario.json): valgono solo per i termini che qui sono ancora quelli
     * predefiniti, così un pacchetto non cambia le scelte già fatte dall'installazione. Restituisce che cosa è
     * successo, in parole.
     */
    @Transactional
    public String importaDalPacchetto(Map<String, Map<String, String>> delPacchetto) {
        if (delPacchetto == null || delPacchetto.isEmpty()) return "";
        Map<String, Map<String, String>> nuove = new LinkedHashMap<>();
        List<String> tenute = new ArrayList<>();
        for (var voce : delPacchetto.entrySet()) {
            if (Termine.da(voce.getKey()).isEmpty()) continue;
            if (repo.existsById(voce.getKey())) tenute.add(voce.getKey());
            else nuove.put(voce.getKey(), voce.getValue());
        }
        if (!nuove.isEmpty()) salva(nuove);
        List<String> applicate = nuove.entrySet().stream()
            .filter(v -> repo.existsById(v.getKey()))
            .map(v -> v.getKey() + " = " + repo.findById(v.getKey()).map(TermineGlossario::getSingolare).orElse("")).toList();
        return (applicate.isEmpty() ? "nessuna parola nuova" : "parole applicate: " + String.join(", ", applicate))
            + (tenute.isEmpty() ? "" : "; tenute quelle già scelte qui: " + String.join(", ", tenute));
    }

    /** Solo i termini scelti dall'installazione, per esportarli in un pacchetto. */
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> scelti() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Termine t : Termine.values())
            repo.findById(t.chiave()).ifPresent(s -> out.put(t.chiave(),
                Map.of("singolare", s.getSingolare(), "plurale", s.getPlurale(), "genere", s.getGenere())));
        return out;
    }

    private static String pulisci(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }

    private static ResponseStatusException bad(String messaggio) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, messaggio);
    }
}
