package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Referenti delle organizzazioni esterne. L'ente (permesso organizzazioni) nomina uno o più referenti tra gli utenti
 * esterni di un'organizzazione; il referente, dal portale, aggiunge, disattiva, riattiva e toglie i suoi colleghi.
 * Il referente non è un ruolo: resta un utente esterno senza permessi, e può agire solo su utenti esterni della
 * sua organizzazione che non sono referenti (né sé stesso). Chi è anche in un'altra organizzazione si può solo
 * togliere dalla sua. Ogni regola si controlla qui, sul server, leggendo il database a ogni richiesta.
 */
@Service
public class ReferentiService {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,}");
    /** Caratteri della password temporanea: niente 0/O, 1/l/I, facili da confondere quando si dettano. */
    private static final String CARATTERI = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom CASO = new SecureRandom();

    /** Una persona dell'organizzazione, come la vede il referente (dati minimi). */
    public record Collega(Long id, String username, String nome, String cognome, String email, boolean attivo,
                          boolean referente, boolean io, boolean altreOrganizzazioni, boolean inAttesa,
                          OffsetDateTime ultimoAccesso) { }

    /** Esito dell'aggiunta: la password temporanea si mostra una volta sola (null se si entra solo dal provider). */
    public record Aggiunta(Collega collega, String passwordTemporanea) { }

    private final OrganizzazioneReferenteRepository referenti;
    private final OrganizzazioneRepository organizzazioni;
    private final AppUserRepository utenti;
    private final CurrentUserService currentUser;
    private final RegistroService registro;
    private final PasswordEncoder encoder;
    private final boolean loginLocale;
    private final int maxUtenti;
    private final int maxAggiunteOra;

    public ReferentiService(OrganizzazioneReferenteRepository referenti, OrganizzazioneRepository organizzazioni,
                            AppUserRepository utenti, CurrentUserService currentUser, RegistroService registro,
                            PasswordEncoder encoder,
                            @Value("${app.login.locale:true}") boolean loginLocale,
                            @Value("${app.organizzazioni.max-utenti:200}") int maxUtenti,
                            @Value("${app.organizzazioni.max-aggiunte-ora:20}") int maxAggiunteOra) {
        this.referenti = referenti;
        this.organizzazioni = organizzazioni;
        this.utenti = utenti;
        this.currentUser = currentUser;
        this.registro = registro;
        this.encoder = encoder;
        this.loginLocale = loginLocale;
        this.maxUtenti = maxUtenti;
        this.maxAggiunteOra = maxAggiunteOra;
    }

    // ---------------------------------------------------------------- per l'ente (Studio → Organizzazioni)

    /** Le persone di un'organizzazione esterna, con chi è referente. */
    public List<Collega> membri(Organizzazione org) {
        Set<Long> ref = idReferenti(org.getId());
        return utenti.membriDi(org.getId()).stream().map(u -> collega(u, org.getId(), ref, null)).toList();
    }

    public long numeroReferenti(Long organizzazioneId) {
        return referenti.countByOrganizzazioneId(organizzazioneId);
    }

    /** Imposta i referenti di un'organizzazione: devono essere utenti esterni che ne fanno parte. */
    @Transactional
    public List<Collega> impostaReferenti(Organizzazione org, Collection<Long> idUtenti) {
        Set<Long> nuovi = new HashSet<>(idUtenti == null ? List.of() : idUtenti);
        for (Long id : nuovi) {
            AppUser u = utenti.findById(id).orElseThrow(() -> bad("Utente non trovato"));
            if (!u.isEsterno() || !u.getOrganizzazioni().contains(org.getId()))
                throw bad("Il referente deve essere un utente esterno di " + org.getNome());
        }
        List<OrganizzazioneReferente> attuali = referenti.findByOrganizzazioneId(org.getId());
        for (OrganizzazioneReferente r : attuali) {
            if (!nuovi.remove(r.getUserId())) {
                referenti.delete(r);
                registro.registra("REFERENTE_REVOCATO", org.getCodice(), nomeUtente(r.getUserId()) + " non è più referente di " + org.getNome());
            }
        }
        for (Long id : nuovi) {
            referenti.save(new OrganizzazioneReferente(org.getId(), id, currentUser.username()));
            registro.registra("REFERENTE_NOMINATO", org.getCodice(), nomeUtente(id) + " è referente di " + org.getNome());
        }
        return membri(org);
    }

    /** Dopo una modifica dell'utente dall'amministrazione: niente referenze per organizzazioni di cui non fa più parte. */
    @Transactional
    public void allinea(AppUser u) {
        if (u.getId() == null) return;
        for (OrganizzazioneReferente r : referenti.findByUserId(u.getId()))
            if (!u.isEsterno() || !u.getOrganizzazioni().contains(r.getOrganizzazioneId())) referenti.delete(r);
    }

    // ---------------------------------------------------------------- per il referente (portale)

    /** Le organizzazioni che l'utente gestisce come referente (attive, e solo se ne fa ancora parte). */
    public List<Organizzazione> gestite(AppUser u) {
        if (u == null || !u.isEsterno() || !u.isEnabled() || u.getId() == null) return List.of();
        List<Long> ids = referenti.findByUserId(u.getId()).stream()
            .map(OrganizzazioneReferente::getOrganizzazioneId)
            .filter(u.getOrganizzazioni()::contains).toList();
        return organizzazioni.findAllById(ids).stream()
            .filter(o -> o.isEsterna() && o.isAttiva())
            .sorted(Comparator.comparing(Organizzazione::getNome)).toList();
    }

    /** L'organizzazione, se l'utente corrente ne è referente; altrimenti 403 (e il tentativo va nel registro). */
    public Organizzazione verificaReferente(Long organizzazioneId) {
        AppUser io = currentUser.current();
        return gestite(io).stream().filter(o -> o.getId().equals(organizzazioneId)).findFirst()
            .orElseThrow(() -> negato(io, "non è referente dell'organizzazione " + organizzazioneId));
    }

    public List<Collega> colleghi(Organizzazione org) {
        AppUser io = currentUser.current();
        Set<Long> ref = idReferenti(org.getId());
        return utenti.membriDi(org.getId()).stream().map(u -> collega(u, org.getId(), ref, io)).toList();
    }

    @Transactional
    public Aggiunta aggiungi(Organizzazione org, String nome, String cognome, String email) {
        AppUser io = currentUser.current();
        String n = testo(nome, "Il nome"), c = testo(cognome, "Il cognome");
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 100 || !EMAIL.matcher(e).matches()) throw bad("Indica un indirizzo email valido");
        if (utenti.membriDi(org.getId()).size() >= maxUtenti)
            throw bad("L'organizzazione ha già " + maxUtenti + " persone, il massimo consentito: chiedi all'ente");
        if (utenti.countByCreatoDaAndCreatoIlAfter(io.getUsername(), OffsetDateTime.now().minusHours(1)) >= maxAggiunteOra)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Hai aggiunto molte persone nell'ultima ora: riprova più tardi");
        // non si dice se il nome è usato da un'altra organizzazione o dal personale
        if (utenti.existsByUsername(e)) throw bad("Nome utente non disponibile: usa un altro indirizzo email o chiedi all'ente");

        AppUser u = new AppUser();
        u.setUsername(e);
        u.setEmail(e);
        u.setFirstName(n);
        u.setLastName(c);
        u.setTipo(AppUser.ESTERNO);
        u.setEnabled(true);
        u.getOrganizzazioni().add(org.getId());
        u.setCreatoDa(io.getUsername());
        u.setCreatoIl(OffsetDateTime.now());
        String temporanea = null;
        if (loginLocale) {
            temporanea = passwordCasuale(14);
            u.setPassword(encoder.encode(temporanea));
            u.setPasswordTemporanea(true);
        } else {
            // entra solo dal provider: al primo accesso con questa email verificata si collega (OrditogramOidcUserService)
            u.setPassword(encoder.encode(UUID.randomUUID() + UUID.randomUUID().toString()));
        }
        AppUser salvato = utenti.save(u);
        registro.registra("UTENTE_ORGANIZZAZIONE_AGGIUNTO", salvato.getUsername(),
            salvato.getFullName() + " aggiunto a " + org.getNome() + " dal referente");
        return new Aggiunta(collega(salvato, org.getId(), idReferenti(org.getId()), io), temporanea);
    }

    @Transactional
    public Collega attiva(Organizzazione org, Long idUtente, boolean attivo) {
        AppUser io = currentUser.current();
        AppUser u = bersaglio(org, idUtente, io);
        if (u.getOrganizzazioni().size() > 1)
            throw bad("Questa persona fa parte anche di un'altra organizzazione: puoi solo toglierla dalla tua");
        if (u.isEnabled() != attivo) {
            u.setEnabled(attivo);
            utenti.save(u);
            registro.registra(attivo ? "UTENTE_ORGANIZZAZIONE_RIATTIVATO" : "UTENTE_ORGANIZZAZIONE_DISATTIVATO", u.getUsername(),
                u.getFullName() + (attivo ? " riattivato" : " disattivato") + " in " + org.getNome() + " dal referente");
        }
        return collega(u, org.getId(), idReferenti(org.getId()), io);
    }

    /** Toglie la persona dall'organizzazione; se non resta in nessuna, viene anche disattivata. */
    @Transactional
    public void togli(Organizzazione org, Long idUtente) {
        AppUser io = currentUser.current();
        AppUser u = bersaglio(org, idUtente, io);
        u.getOrganizzazioni().remove(org.getId());
        boolean disattivato = u.getOrganizzazioni().isEmpty() && u.isEnabled();
        if (disattivato) u.setEnabled(false);
        utenti.save(u);
        registro.registra("UTENTE_ORGANIZZAZIONE_TOLTO", u.getUsername(),
            u.getFullName() + " tolto da " + org.getNome() + " dal referente" + (disattivato ? " (e disattivato)" : ""));
    }

    /** Un collega su cui il referente può agire: esterno, della sua organizzazione, non referente, non sé stesso. */
    private AppUser bersaglio(Organizzazione org, Long idUtente, AppUser io) {
        AppUser u = utenti.findById(idUtente).filter(x -> x.getOrganizzazioni().contains(org.getId()))
            .orElseThrow(() -> {
                negato(io, "utente " + idUtente + " non è di " + org.getCodice());
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona non trovata");
            });
        if (u.getId().equals(io.getId())) throw bad("Non puoi modificare te stesso");
        if (!u.isEsterno()) throw negato(io, "utente interno " + u.getUsername());
        if (referenti.existsByOrganizzazioneIdAndUserId(org.getId(), u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Un altro referente si gestisce solo dall'ente");
        return u;
    }

    private Collega collega(AppUser u, Long orgId, Set<Long> ref, AppUser io) {
        return new Collega(u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(), u.getEmail(), u.isEnabled(),
            ref.contains(u.getId()), io != null && io.getId().equals(u.getId()),
            u.getOrganizzazioni().stream().anyMatch(o -> !o.equals(orgId)),
            u.getUltimoAccesso() == null, u.getUltimoAccesso());
    }

    private Set<Long> idReferenti(Long orgId) {
        Set<Long> ids = new HashSet<>();
        referenti.findByOrganizzazioneId(orgId).forEach(r -> ids.add(r.getUserId()));
        return ids;
    }

    private String nomeUtente(Long id) {
        return utenti.findById(id).map(u -> u.getFullName() + " (" + u.getUsername() + ")").orElse("utente " + id);
    }

    private ResponseStatusException negato(AppUser io, String motivo) {
        registro.registraEvento("ACCESSO_NEGATO", io.getUsername(), "Referente: " + motivo);
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Operazione non consentita");
    }

    static String passwordCasuale(int lunghezza) {
        StringBuilder sb = new StringBuilder(lunghezza);
        for (int i = 0; i < lunghezza; i++) sb.append(CARATTERI.charAt(CASO.nextInt(CARATTERI.length())));
        return sb.toString();
    }

    private static String testo(String s, String cosa) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty() || t.length() > 100) throw bad(cosa + " è obbligatorio (al massimo 100 caratteri)");
        return t;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
