package com.eligesoft.orditogram.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utenti di prova e accessi rapidi della pagina di accesso. Gli accessi rapidi si vedono solo se valgono
 * insieme: utenti di prova accesi, accesso con password, profilo non di produzione e indirizzo localhost.
 * Una soluzione porta i suoi attori nel pacchetto (utenti-prova.json): si creano solo con gli utenti di prova
 * accesi, con la password "password", e non cambiano mai un utente che esiste già.
 */
@Service
public class UtentiProvaService {

    /** Un attore della demo, come sta in utenti-prova.json e come arriva alla pagina di accesso. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attore(String username, String nome, String cognome, String ruolo, List<String> gruppi, Boolean esterno) { }

    /** Gli utenti di prova del nucleo mostrati quando nessuna soluzione ha portato i suoi. */
    static final List<Attore> NUCLEO = List.of(
        new Attore("mario", "Mario", "Rossi", "Operatore", null, null),
        new Attore("laura", "Laura", "Verdi", "Dirigente", null, null),
        new Attore("paolo", "Paolo", "Neri", "Analista", null, null),
        new Attore("marco", "Marco", "Ferri", "Utente esterno", null, null),
        new Attore("admin", "admin", "admin", "Amministratore", null, null));

    private static final Pattern USERNAME = Pattern.compile("[a-z][a-z0-9._-]{1,59}");
    private static final Pattern GRUPPO = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._:-]{0,99}");
    private static final Set<String> LOCALI = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    private final AppUserRepository utenti;
    private final UtenteProvaRepository attori;
    private final PasswordEncoder encoder;
    private final boolean demo;
    private final boolean locale;
    private final boolean produzione;

    public UtentiProvaService(AppUserRepository utenti, UtenteProvaRepository attori, PasswordEncoder encoder,
                              Environment ambiente,
                              @Value("${app.demo.utenti:false}") boolean demo,
                              @Value("${app.login.locale:true}") boolean locale) {
        this.utenti = utenti;
        this.attori = attori;
        this.encoder = encoder;
        this.demo = demo;
        this.locale = locale;
        this.produzione = ambiente.acceptsProfiles(Profiles.of("produzione"));
    }

    /** Si mostrano gli accessi rapidi a chi apre l'applicazione da questo indirizzo? */
    public boolean accessiRapidi(String indirizzo) {
        return demo && locale && !produzione && indirizzo != null && LOCALI.contains(indirizzo.toLowerCase(Locale.ROOT));
    }

    /** Gli attori da mostrare: quelli delle soluzioni importate, altrimenti gli utenti di prova del nucleo. */
    @Transactional(readOnly = true)
    public List<Map<String, String>> elenco() {
        List<Map<String, String>> out = new ArrayList<>();
        for (UtenteProva a : attori.findAllByOrderByOrdineAscUsernameAsc())
            utenti.findByUsername(a.getUsername()).filter(AppUser::isEnabled)
                .ifPresent(u -> out.add(voce(u.getUsername(), a.getRuolo(), nomeDi(u))));
        if (!out.isEmpty()) out.add(voce("admin", "Amministratore", "admin / admin"));
        else for (Attore a : NUCLEO)
            if (utenti.existsByUsername(a.username()))
                out.add(voce(a.username(), a.ruolo(), "admin".equals(a.username()) ? "admin / admin" : a.nome() + " " + a.cognome()));
        return out;
    }

    /** Gli attori di una soluzione, per esportarli nel suo pacchetto. */
    @Transactional(readOnly = true)
    public List<Attore> diSoluzione(String chiave) {
        List<Attore> out = new ArrayList<>();
        for (UtenteProva a : attori.findByOrigineOrderByOrdineAsc(chiave))
            utenti.findByUsername(a.getUsername()).ifPresent(u -> out.add(new Attore(u.getUsername(), u.getFirstName(),
                u.getLastName(), a.getRuolo(), u.getGroups().stream().sorted().toList(), u.isEsterno() ? true : null)));
        return out;
    }

    /**
     * Attori di un pacchetto. Con gli utenti di prova spenti (produzione, clienti) non si crea nessuno; chi c'è
     * già resta com'è, si aggiunge solo il suo ruolo negli accessi rapidi. Restituisce che cosa è successo.
     */
    @Transactional
    public String importa(List<Attore> delPacchetto, String origine) {
        if (delPacchetto == null || delPacchetto.isEmpty()) return "";
        if (!demo) return "non creati: gli utenti di prova sono spenti in questa installazione";
        List<String> creati = new ArrayList<>();
        List<String> esistenti = new ArrayList<>();
        int ordine = 0;
        for (Attore a : delPacchetto) {
            ordine++;
            String username = Objects.toString(a.username(), "").trim();
            String ruolo = Objects.toString(a.ruolo(), "").strip();
            if (!USERNAME.matcher(username).matches() || "admin".equals(username))
                throw new IllegalArgumentException("utenti-prova.json: nome utente non valido: " + username);
            if (ruolo.isEmpty() || ruolo.length() > 80)
                throw new IllegalArgumentException("utenti-prova.json: indica il ruolo di " + username + " (al massimo 80 caratteri)");
            List<String> gruppi = a.gruppi() != null ? a.gruppi() : List.of();
            for (String g : gruppi)
                if (g == null || !GRUPPO.matcher(g).matches() || g.startsWith("org:") || "esterni".equals(g))
                    throw new IllegalArgumentException("utenti-prova.json: gruppo non valido per " + username + ": " + g);
            if (utenti.existsByUsername(username)) esistenti.add(username);
            else {
                AppUser u = new AppUser();
                u.setUsername(username);
                u.setPassword(encoder.encode("password"));
                u.setFirstName(testo(a.nome(), username));
                u.setLastName(testo(a.cognome(), ""));
                u.setGroups(new HashSet<>(gruppi));
                if (Boolean.TRUE.equals(a.esterno())) u.setTipo(AppUser.ESTERNO);
                utenti.save(u);
                creati.add(username);
            }
            attori.save(new UtenteProva(username, ruolo, ordine, origine));
        }
        return (creati.isEmpty() ? "nessun utente nuovo" : "creati con la password \"password\": " + String.join(", ", creati))
            + (esistenti.isEmpty() ? "" : "; già presenti, lasciati come sono: " + String.join(", ", esistenti));
    }

    private static String testo(String s, String altrimenti) {
        String t = s == null ? "" : s.strip();
        return t.isEmpty() ? altrimenti : t.length() > 100 ? t.substring(0, 100) : t;
    }

    private static String nomeDi(AppUser u) {
        String n = (Objects.toString(u.getFirstName(), "") + " " + Objects.toString(u.getLastName(), "")).strip();
        return n.isEmpty() ? u.getUsername() : n;
    }

    private static Map<String, String> voce(String username, String ruolo, String nome) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("username", username);
        m.put("ruolo", ruolo);
        m.put("nome", nome);
        return m;
    }
}
