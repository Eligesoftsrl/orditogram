package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.messaggio.MessaggiProperties;
import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Chiavi dell'API pubblica: creazione (il valore si mostra una volta sola), revoca, rotazione, verifica.
 * Valgono anche le chiavi scritte in configurazione (app.messaggi.chiavi), ma solo per inviare messaggi,
 * come nelle versioni precedenti.
 */
@Service
public class ChiaviApiService {

    public static final String INIZIO = "iter_";
    private static final int LUNGHEZZA_CASUALE = 40;
    private static final int LUNGHEZZA_PREFISSO = 12;
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern NOME = Pattern.compile("[a-z0-9][a-z0-9-]{1,59}");
    private static final SecureRandom CASO = new SecureRandom();

    private final ChiaveApiRepository repo;
    private final MessaggiProperties chiaviConfigurate;
    private final CurrentUserService currentUser;
    private final RegistroService registro;
    /** Ultimo aggiornamento di "ultimo uso" per chiave: si scrive sul database al massimo una volta al minuto. */
    private final Map<Long, Instant> ultimiUsi = new ConcurrentHashMap<>();

    public ChiaviApiService(ChiaveApiRepository repo, MessaggiProperties chiaviConfigurate,
                            CurrentUserService currentUser, RegistroService registro) {
        this.repo = repo;
        this.chiaviConfigurate = chiaviConfigurate;
        this.currentUser = currentUser;
        this.registro = registro;
    }

    public record Creata(ChiaveApi chiave, String valore) {}

    // ------------------------------------------------------------------ gestione (Studio)

    @Transactional
    public Creata crea(String nome, String descrizione, Collection<String> ambiti, Collection<String> processi,
                       LocalDate scadenza) {
        String n = nome == null ? "" : nome.trim().toLowerCase(Locale.ROOT);
        if (!NOME.matcher(n).matches())
            throw bad("Il nome deve avere da 2 a 60 caratteri: lettere minuscole, numeri e trattini (es. portale-cittadino)");
        if (repo.findByNome(n).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esiste già una chiave con questo nome");
        ChiaveApi c = new ChiaveApi();
        c.setNome(n);
        c.setCreata(OffsetDateTime.now());
        c.setCreataDa(currentUser.username());
        applica(c, descrizione, ambiti, processi, scadenza);
        String valore = nuovoValore();
        c.setPrefisso(valore.substring(0, LUNGHEZZA_PREFISSO));
        c.setImpronta(impronta(valore));
        repo.save(c);
        registro.registra("CHIAVE_API_CREATA", n, "Permessi: " + c.getAmbiti() + processiTesto(c));
        return new Creata(c, valore);
    }

    @Transactional
    public ChiaveApi modifica(Long id, String descrizione, Collection<String> ambiti, Collection<String> processi,
                              LocalDate scadenza, boolean attiva) {
        ChiaveApi c = trova(id);
        if (c.getRevocata() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "La chiave è revocata");
        applica(c, descrizione, ambiti, processi, scadenza);
        c.setAttiva(attiva);
        registro.registra("CHIAVE_API_MODIFICATA", c.getNome(),
            (attiva ? "Attiva" : "Sospesa") + ". Permessi: " + c.getAmbiti() + processiTesto(c));
        return c;
    }

    /** Nuovo valore per la stessa chiave (es. se quello vecchio è stato esposto): il vecchio smette subito di funzionare. */
    @Transactional
    public Creata ruota(Long id) {
        ChiaveApi c = trova(id);
        if (c.getRevocata() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "La chiave è revocata");
        String valore = nuovoValore();
        c.setPrefisso(valore.substring(0, LUNGHEZZA_PREFISSO));
        c.setImpronta(impronta(valore));
        registro.registra("CHIAVE_API_RUOTATA", c.getNome(), "Nuovo valore generato, il precedente non è più valido");
        return new Creata(c, valore);
    }

    @Transactional
    public ChiaveApi revoca(Long id) {
        ChiaveApi c = trova(id);
        if (c.getRevocata() == null) {
            c.setRevocata(OffsetDateTime.now());
            c.setAttiva(false);
            registro.registra("CHIAVE_API_REVOCATA", c.getNome(), null);
        }
        return c;
    }

    public List<ChiaveApi> elenco() {
        return repo.findAllByOrderByRevocataAscNomeAsc();
    }

    /** Sistemi configurati sul server (app.messaggi.chiavi), mostrati nello Studio in sola lettura. */
    public List<String> sistemiConfigurati() {
        return chiaviConfigurate.getChiavi().entrySet().stream()
            .filter(e -> e.getValue() != null && !e.getValue().isBlank())
            .map(Map.Entry::getKey).sorted().toList();
    }

    private ChiaveApi trova(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chiave non trovata"));
    }

    private static void applica(ChiaveApi c, String descrizione, Collection<String> ambiti, Collection<String> processi,
                                LocalDate scadenza) {
        Set<String> a = new LinkedHashSet<>();
        for (String x : ambiti == null ? List.<String>of() : ambiti) {
            if (!Ambiti.TUTTI.containsKey(x)) throw bad("Permesso sconosciuto: " + x);
            a.add(x);
        }
        if (a.isEmpty()) throw bad("Scegli almeno un permesso");
        Set<String> p = new LinkedHashSet<>();
        for (String x : processi == null ? List.<String>of() : processi) {
            if (x == null || x.isBlank()) continue;
            if (!x.trim().matches("[\\w.-]+")) throw bad("Chiave di processo non valida: " + x);
            p.add(x.trim());
        }
        c.setDescrizione(descrizione == null || descrizione.isBlank() ? null : descrizione.trim());
        c.setAmbiti(String.join(",", a));
        c.setProcessi(p.isEmpty() ? null : String.join(",", p));
        c.setScadenza(scadenza);
    }

    private static String processiTesto(ChiaveApi c) {
        return c.getProcessi() == null ? ". Tutti i processi" : ". Processi: " + c.getProcessi();
    }

    // ------------------------------------------------------------------ verifica (a ogni richiesta)

    /** Il sistema a cui appartiene la chiave, se è valida. */
    public Optional<IdentitaApi> verifica(String valore) {
        if (valore == null || valore.isBlank()) return Optional.empty();
        String v = valore.trim();
        if (v.startsWith(INIZIO) && v.length() == INIZIO.length() + LUNGHEZZA_CASUALE) {
            byte[] attesa = impronta(v).getBytes(StandardCharsets.US_ASCII);
            for (ChiaveApi c : repo.findByPrefisso(v.substring(0, LUNGHEZZA_PREFISSO))) {
                if (!MessageDigest.isEqual(attesa, c.getImpronta().getBytes(StandardCharsets.US_ASCII))) continue;
                if (!c.valida(LocalDate.now())) return Optional.empty();
                return Optional.of(new IdentitaApi(c.getId(), c.getNome(), c.ambitiSet(), c.processiSet()));
            }
            return Optional.empty();
        }
        // chiavi in configurazione: solo messaggi, come prima dell'API pubblica
        return chiaviConfigurate.sistema(v)
            .map(nome -> new IdentitaApi(null, nome, Set.of(Ambiti.MESSAGGI_INVIA), Set.of()));
    }

    /** Annota ultimo uso e indirizzo, al massimo una volta al minuto per chiave. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void segnaUso(IdentitaApi id, String indirizzo) {
        if (id.chiaveId() == null) return;
        Instant ora = Instant.now();
        Instant prima = ultimiUsi.get(id.chiaveId());
        if (prima != null && prima.isAfter(ora.minusSeconds(60))) return;
        ultimiUsi.put(id.chiaveId(), ora);
        repo.segnaUso(id.chiaveId(), OffsetDateTime.now(), indirizzo == null || indirizzo.length() <= 64 ? indirizzo : indirizzo.substring(0, 64));
    }

    // ------------------------------------------------------------------ supporto

    static String nuovoValore() {
        StringBuilder sb = new StringBuilder(INIZIO);
        for (int i = 0; i < LUNGHEZZA_CASUALE; i++) sb.append(ALFABETO.charAt(CASO.nextInt(ALFABETO.length())));
        return sb.toString();
    }

    static String impronta(String valore) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valore.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
