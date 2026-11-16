package com.eligesoft.orditogram.anagrafica;

import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.user.AppUserRepository;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Le schede delle anagrafiche: ricerca, lettura, creazione, modifica, cessazione e versioni.
 * Ogni operazione controlla i permessi dell'utente sul tipo (AccessoAnagrafiche): un tipo o una scheda che
 * l'utente non può vedere risponde "non trovata", come se non esistesse. I dati passano dalle stesse regole dei
 * form (ValoriForm), i campi riservati ad alcuni ruoli non si vedono né si cambiano, le relazioni puntano solo a
 * schede attive che l'utente può vedere.
 */
@Service
public class SchedeService {

    /** Dimensione massima dei dati di una scheda (JSON). */
    static final int MAX_DATI = 512 * 1024;
    static final int MAX_PAGINA = 100;
    static final int MAX_CITAZIONI = 100;

    public record TipoVisibile(String codice, String nome, String descrizione, boolean attivo, int versione,
                               JsonNode definizione, Set<String> azioni) { }

    public record Riga(Long id, String titolo, String stato, OffsetDateTime modificataIl) { }

    public record Pagina(List<Riga> schede, long totale, int pagina, int dimensione) { }

    /** Una scheda collegata: titolo e stato solo se l'utente può vedere il suo tipo. */
    public record Collegamento(Long id, String tipo, String titolo, String stato, boolean visibile) { }

    /** Una scheda di un altro tipo (o dello stesso) che cita questa in un campo relazione. */
    public record Citazione(String tipo, String tipoNome, String campo, String etichetta, Long id, String titolo, String stato) { }

    public record Scheda(Long id, String tipo, String titolo, String stato, long versione, Map<String, Object> dati,
                         Map<String, Collegamento> collegamenti, List<Citazione> citataDa, Set<String> azioni,
                         OffsetDateTime creataIl, String creataDa, OffsetDateTime modificataIl, String modificataDa) { }

    public record Versione(long numero, String operazione, String stato, Map<String, Object> dati, int versioneTipo,
                           OffsetDateTime il, String utente, String utenteNome, String nota) { }

    private final TipoAnagraficaRepository tipi;
    private final SchedaAnagraficaRepository schede;
    private final IndiceAnagraficaRepository indice;
    private final VersioneSchedaRepository versioni;
    private final AccessoAnagrafiche accesso;
    private final AppUserRepository utenti;
    private final CurrentUserService utenteCorrente;
    private final ObjectMapper mapper;

    public SchedeService(TipoAnagraficaRepository tipi, SchedaAnagraficaRepository schede, IndiceAnagraficaRepository indice,
                         VersioneSchedaRepository versioni, AccessoAnagrafiche accesso, AppUserRepository utenti,
                         CurrentUserService utenteCorrente, ObjectMapper mapper) {
        this.tipi = tipi;
        this.schede = schede;
        this.indice = indice;
        this.versioni = versioni;
        this.accesso = accesso;
        this.utenti = utenti;
        this.utenteCorrente = utenteCorrente;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ tipi

    /** I tipi su cui l'utente ha almeno un'azione, con la definizione senza i campi a lui riservati. */
    @Transactional(readOnly = true)
    public List<TipoVisibile> tipi() {
        AccessoAnagrafiche.Utente u = io();
        List<TipoVisibile> out = new ArrayList<>();
        for (TipoAnagrafica t : tipi.findAllByOrderByNomeAsc()) {
            Set<String> azioni = accesso.azioni(u, t);
            if (!azioni.isEmpty()) out.add(visibile(t, azioni, u));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public TipoVisibile tipo(String codice) {
        AccessoAnagrafiche.Utente u = io();
        TipoAnagrafica t = tipoPer(u, codice, PermessoAnagrafica.VEDE);
        return visibile(t, accesso.azioni(u, t), u);
    }

    // ------------------------------------------------------------ ricerca e lettura

    /**
     * Cerca le schede del tipo. testo: nel titolo e nei campi ricercabili o chiave (contiene, senza maiuscole);
     * campo e valore: uguaglianza esatta su un campo chiave, ricercabile o relazione (es. codice fiscale);
     * stato: ATTIVA (predefinito), CESSATA o TUTTE.
     */
    @Transactional(readOnly = true)
    public Pagina cerca(String codice, String testo, String campo, String valore, String stato, int pagina, int dimensione) {
        AccessoAnagrafiche.Utente u = io();
        TipoAnagrafica t = tipoPer(u, codice, PermessoAnagrafica.VEDE);
        DefinizioneAnagrafica def = definizione(t);
        String filtroStato = switch (stato == null || stato.isBlank() ? SchedaAnagrafica.ATTIVA : stato.trim().toUpperCase(Locale.ROOT)) {
            case SchedaAnagrafica.ATTIVA -> SchedaAnagrafica.ATTIVA;
            case SchedaAnagrafica.CESSATA -> SchedaAnagrafica.CESSATA;
            case "TUTTE" -> null;
            default -> throw bad("Stato sconosciuto: usa ATTIVA, CESSATA o TUTTE");
        };
        String like = null;
        String normalizzato = DefinizioneAnagrafica.normalizza(testo);
        if (normalizzato != null) {
            if (normalizzato.length() > 100) throw bad("Testo da cercare troppo lungo (al massimo 100 caratteri)");
            like = "%" + normalizzato.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        }
        String filtroCampo = null, filtroValore = null;
        if (campo != null && !campo.isBlank()) {
            if (def.indice().stream().noneMatch(c -> c.nome().equals(campo)))
                throw bad("Si cerca per valore solo nei campi chiave, ricercabili o collegati");
            filtroCampo = campo;
            filtroValore = DefinizioneAnagrafica.normalizza(valore);
            if (filtroValore == null) throw bad("Indica il valore da cercare");
        }
        if (pagina < 0) pagina = 0;
        if (dimensione < 1 || dimensione > MAX_PAGINA) dimensione = Math.min(Math.max(dimensione, 1), MAX_PAGINA);
        Page<SchedaAnagrafica> p = schede.cerca(t, filtroStato, like, filtroCampo, filtroValore,
            PageRequest.of(pagina, dimensione, Sort.by("titolo").ascending().and(Sort.by("id"))));
        return new Pagina(p.getContent().stream().map(s -> new Riga(s.getId(), s.getTitolo(), s.getStato(), s.getModificataIl())).toList(),
            p.getTotalElements(), pagina, dimensione);
    }

    @Transactional(readOnly = true)
    public Scheda leggi(String codice, Long id) {
        AccessoAnagrafiche.Utente u = io();
        TipoAnagrafica t = tipoPer(u, codice, PermessoAnagrafica.VEDE);
        return dto(u, t, schedaDi(t, id));
    }

    @Transactional(readOnly = true)
    public List<Versione> versioni(String codice, Long id) {
        AccessoAnagrafiche.Utente u = io();
        TipoAnagrafica t = tipoPer(u, codice, PermessoAnagrafica.VEDE);
        SchedaAnagrafica s = schedaDi(t, id);
        Set<String> riservati = definizione(t).riservati(u.ruoli(), u.amministratore());
        Map<String, String> nomi = new HashMap<>();
        return versioni.findBySchedaIdOrderByNumeroDesc(s.getId()).stream().map(v -> {
            Map<String, Object> dati = leggiMappa(v.getDati());
            dati.keySet().removeAll(riservati);
            String nome = v.getUtente() == null ? null : nomi.computeIfAbsent(v.getUtente(), utenteCorrente::fullNameOf);
            return new Versione(v.getNumero(), v.getOperazione(), v.getStato(), dati, v.getVersioneTipo(), v.getIl(),
                v.getUtente(), nome, v.getNota());
        }).toList();
    }

    // ------------------------------------------------------------ modifiche

    @Transactional
    public Scheda crea(String codice, Map<String, Object> dati) {
        AccessoAnagrafiche.Utente u = io();
        TipoAnagrafica t = tipoPer(u, codice, PermessoAnagrafica.MODIFICA);
        if (!t.isAttivo()) throw conflitto("L'anagrafica è disattivata: non si possono aggiungere schede");
        DefinizioneAnagrafica def = definizione(t);
        Map<String, Object> valori = valori(u, def, dati, Map.of(), true);
        SchedaAnagrafica s = new SchedaAnagrafica();
        s.setTipo(t);
        s.setDati(testo(valori));
        s.setTitolo(titolo(def, t, valori));
        OffsetDateTime ora = OffsetDateTime.now();
        s.setCreataIl(ora);
        s.setCreataDa(u.username());
        s.setModificataIl(ora);
        s.setModificataDa(u.username());
        controllaChiavi(def, t, null, valori);
        s = schede.saveAndFlush(s);
        indicizza(def, t, s, valori);
        versioni.save(new VersioneScheda(s, 1, VersioneScheda.CREATA, u.username(), null));
        return dto(u, t, s);
    }

    /** Modifica i dati; versione è quella che l'utente ha letto: se nel frattempo è cambiata, la modifica è rifiutata. */
    @Transactional
    public Scheda modifica(String codice, Long id, Long versione, Map<String, Object> dati, String nota) {
        AccessoAnagrafiche.Utente u = io();
        TipoAnagrafica t = tipoPer(u, codice, PermessoAnagrafica.MODIFICA);
        SchedaAnagrafica s = schedaDi(t, id);
        controllaVersione(s, versione);
        if (!s.isAttiva()) throw conflitto("La scheda è cessata: riattivala per modificarla");
        DefinizioneAnagrafica def = definizione(t);
        Map<String, Object> prima = leggiMappa(s.getDati());
        Map<String, Object> valori = valori(u, def, dati, prima, false);
        String testo = testo(valori);
        if (testo.equals(s.getDati())) return dto(u, t, s);
        controllaChiavi(def, t, s.getId(), valori);
        s.setTitolo(titolo(def, t, valori));
        s.setDati(testo);
        s.setModificataIl(OffsetDateTime.now());
        s.setModificataDa(u.username());
        s = schede.saveAndFlush(s);
        indicizza(def, t, s, valori);
        versioni.save(new VersioneScheda(s, versioni.countBySchedaId(s.getId()) + 1, VersioneScheda.MODIFICATA, u.username(), nota(nota)));
        return dto(u, t, s);
    }

    /** Cessa (attiva = false) o riattiva una scheda. La nota (es. il motivo) resta nella versione. */
    @Transactional
    public Scheda cambiaStato(String codice, Long id, Long versione, boolean attiva, String nota) {
        AccessoAnagrafiche.Utente u = io();
        TipoAnagrafica t = tipoPer(u, codice, PermessoAnagrafica.MODIFICA);
        SchedaAnagrafica s = schedaDi(t, id);
        controllaVersione(s, versione);
        if (s.isAttiva() == attiva) throw conflitto(attiva ? "La scheda è già attiva" : "La scheda è già cessata");
        s.setStato(attiva ? SchedaAnagrafica.ATTIVA : SchedaAnagrafica.CESSATA);
        s.setModificataIl(OffsetDateTime.now());
        s.setModificataDa(u.username());
        s = schede.saveAndFlush(s);
        versioni.save(new VersioneScheda(s, versioni.countBySchedaId(s.getId()) + 1,
            attiva ? VersioneScheda.RIATTIVATA : VersioneScheda.CESSATA, u.username(), nota(nota)));
        return dto(u, t, s);
    }

    // ------------------------------------------------------------ dati

    /**
     * I valori da salvare: quelli inviati, controllati con le regole dei form, più quelli che l'utente non può
     * cambiare (campi riservati ad altri ruoli e in sola lettura), che restano come erano.
     */
    private Map<String, Object> valori(AccessoAnagrafiche.Utente u, DefinizioneAnagrafica def, Map<String, Object> inviati,
                                       Map<String, Object> prima, boolean nuova) {
        Map<String, Object> in = inviati == null ? Map.of() : inviati;
        Set<String> riservati = def.riservati(u.ruoli(), u.amministratore());
        Set<String> solaLettura = def.solaLettura();
        List<DatiNonValidi.Errore> errori = new ArrayList<>();
        Map<String, Object> daControllare = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            if (riservati.contains(e.getKey())) errori.add(new DatiNonValidi.Errore(e.getKey(), "campo non previsto dal form"));
            else daControllare.put(e.getKey(), e.getValue());
        }
        for (String r : riservati) if (prima.containsKey(r)) daControllare.put(r, prima.get(r));
        // i campi in sola lettura non si inviano: ValoriForm li rifiuterebbe
        daControllare.keySet().removeAll(solaLettura);
        ValoriForm.Esito esito = ValoriForm.convalida(def.json(), daControllare);
        for (ValoriForm.Errore e : esito.errori())
            // un campo riservato obbligatorio e vuoto non è colpa di chi non lo vede: lo segnala con il suo nome solo a chi lo vede
            errori.add(new DatiNonValidi.Errore(e.campo(), riservati.contains(e.campo()) ? "manca un dato riservato: chiedi a chi può vederlo" : e.messaggio()));
        Map<String, Object> valori = new LinkedHashMap<>(esito.valori());
        for (String s : solaLettura) if (prima.containsKey(s)) valori.put(s, prima.get(s));
        if (errori.isEmpty()) controllaRelazioni(u, def, valori, prima, nuova, errori);
        if (!errori.isEmpty()) throw new DatiNonValidi("Controlla i dati della scheda", errori);
        if (testo(valori).length() > MAX_DATI) throw bad("I dati della scheda sono troppo grandi");
        return valori;
    }

    /**
     * Un campo relazione nuovo o cambiato deve puntare a una scheda attiva del tipo previsto che l'utente può vedere.
     * Le schede che non esistono e quelle che non può vedere danno lo stesso errore.
     */
    private void controllaRelazioni(AccessoAnagrafiche.Utente u, DefinizioneAnagrafica def, Map<String, Object> valori,
                                    Map<String, Object> prima, boolean nuova, List<DatiNonValidi.Errore> errori) {
        for (var c : def.indice()) {
            if (!c.relazione()) continue;
            Object v = valori.get(c.nome());
            if (v == null || (!nuova && v.equals(prima.get(c.nome())))) continue;
            SchedaAnagrafica collegata = schede.findById(Long.valueOf(v.toString())).orElse(null);
            boolean valida = collegata != null && collegata.getTipo().getCodice().equals(c.tipoAnagrafica())
                && accesso.puo(u, collegata.getTipo(), PermessoAnagrafica.VEDE);
            if (!valida) errori.add(new DatiNonValidi.Errore(c.nome(), "scheda non trovata"));
            else if (!collegata.isAttiva()) errori.add(new DatiNonValidi.Errore(c.nome(), "la scheda \"" + collegata.getTitolo() + "\" è cessata"));
        }
    }

    /** Nessun'altra scheda dello stesso tipo può avere lo stesso valore in un campo chiave. */
    private void controllaChiavi(DefinizioneAnagrafica def, TipoAnagrafica t, Long id, Map<String, Object> valori) {
        List<DatiNonValidi.Errore> errori = new ArrayList<>();
        for (var c : def.indice()) {
            if (!c.chiave()) continue;
            String v = DefinizioneAnagrafica.normalizza(valori.get(c.nome()));
            if (v == null) continue;
            Optional<IndiceAnagrafica> altra = id == null
                ? indice.findFirstByTipoIdAndCampoAndChiave(t.getId(), c.nome(), v)
                : indice.findFirstByTipoIdAndCampoAndChiaveAndSchedaIdNot(t.getId(), c.nome(), v, id);
            altra.flatMap(i -> schede.findById(i.getSchedaId())).ifPresent(s ->
                errori.add(new DatiNonValidi.Errore(c.nome(), "c'è già la scheda \"" + s.getTitolo() + "\" con questo valore")));
        }
        if (!errori.isEmpty()) throw new DatiNonValidi("Scheda già presente", errori);
    }

    private void indicizza(DefinizioneAnagrafica def, TipoAnagrafica t, SchedaAnagrafica s, Map<String, Object> valori) {
        indice.cancellaDellaScheda(s.getId());
        indice.flush();
        indice.saveAll(IndiciScheda.calcola(def, t.getId(), s.getId(), valori));
    }

    private Scheda dto(AccessoAnagrafiche.Utente u, TipoAnagrafica t, SchedaAnagrafica s) {
        DefinizioneAnagrafica def = definizione(t);
        Set<String> riservati = def.riservati(u.ruoli(), u.amministratore());
        Map<String, Object> dati = leggiMappa(s.getDati());
        dati.keySet().removeIf(k -> riservati.contains(k) || !def.nomi().contains(k));
        Map<Long, Set<String>> azioniPerTipo = new HashMap<>();
        Map<String, Collegamento> collegamenti = new LinkedHashMap<>();
        for (var c : def.indice()) {
            if (!c.relazione() || dati.get(c.nome()) == null) continue;
            Long idCollegata = Long.valueOf(dati.get(c.nome()).toString());
            SchedaAnagrafica collegata = schede.findById(idCollegata).orElse(null);
            boolean visibile = collegata != null && azioniPerTipo.computeIfAbsent(collegata.getTipo().getId(),
                k -> accesso.azioni(u, collegata.getTipo())).contains(PermessoAnagrafica.VEDE);
            collegamenti.put(c.nome(), visibile
                ? new Collegamento(idCollegata, collegata.getTipo().getCodice(), collegata.getTitolo(), collegata.getStato(), true)
                : new Collegamento(idCollegata, c.tipoAnagrafica(), null, null, false));
        }
        return new Scheda(s.getId(), t.getCodice(), s.getTitolo(), s.getStato(), s.getVersione(), dati, collegamenti,
            citazioni(u, t, s), accesso.azioni(u, t), s.getCreataIl(), utenteCorrente.fullNameOf(s.getCreataDa()),
            s.getModificataIl(), utenteCorrente.fullNameOf(s.getModificataDa()));
    }

    /** Le schede che citano questa nei loro campi relazione, solo dei tipi che l'utente può vedere. */
    private List<Citazione> citazioni(AccessoAnagrafiche.Utente u, TipoAnagrafica t, SchedaAnagrafica s) {
        Map<Long, TipoAnagrafica> tipiPerId = new HashMap<>();
        Map<String, String> etichette = new HashMap<>();
        Set<String> coppie = new HashSet<>();
        for (TipoAnagrafica altro : tipi.findAll()) {
            if (!accesso.puo(u, altro, PermessoAnagrafica.VEDE)) continue;
            for (var c : definizione(altro).indice())
                if (c.relazione() && c.tipoAnagrafica().equals(t.getCodice())) {
                    tipiPerId.put(altro.getId(), altro);
                    coppie.add(altro.getId() + "|" + c.nome());
                    etichette.put(altro.getId() + "|" + c.nome(), c.etichetta());
                }
        }
        if (coppie.isEmpty()) return List.of();
        Set<String> campi = new HashSet<>();
        coppie.forEach(k -> campi.add(k.substring(k.indexOf('|') + 1)));
        List<IndiceAnagrafica> righe = indice.findByTipoIdInAndCampoInAndValore(tipiPerId.keySet(), campi, s.getId().toString())
            .stream().filter(i -> coppie.contains(i.getTipoId() + "|" + i.getCampo())).limit(MAX_CITAZIONI).toList();
        Map<Long, SchedaAnagrafica> citanti = new HashMap<>();
        schede.findByIdIn(righe.stream().map(IndiceAnagrafica::getSchedaId).toList()).forEach(x -> citanti.put(x.getId(), x));
        List<Citazione> out = new ArrayList<>();
        for (IndiceAnagrafica i : righe) {
            SchedaAnagrafica x = citanti.get(i.getSchedaId());
            TipoAnagrafica tx = tipiPerId.get(i.getTipoId());
            if (x == null) continue;
            out.add(new Citazione(tx.getCodice(), tx.getNome(), i.getCampo(), etichette.get(i.getTipoId() + "|" + i.getCampo()),
                x.getId(), x.getTitolo(), x.getStato()));
        }
        out.sort(Comparator.comparing(Citazione::tipoNome).thenComparing(Citazione::titolo));
        return out;
    }

    // ------------------------------------------------------------ supporto

    private AccessoAnagrafiche.Utente io() {
        return accesso.utente(utenti.findByUsername(utenteCorrente.username()).orElse(null));
    }

    /** Il tipo, se l'utente ha l'azione indicata; se non lo vede proprio, "non trovata". */
    private TipoAnagrafica tipoPer(AccessoAnagrafiche.Utente u, String codice, String azione) {
        TipoAnagrafica t = tipi.findByCodice(codice == null ? "" : codice).orElseThrow(SchedeService::nonTrovata);
        Set<String> azioni = accesso.azioni(u, t);
        if (azioni.isEmpty()) throw nonTrovata();
        if (!azioni.contains(azione)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            PermessoAnagrafica.ESPORTA.equals(azione) ? "Non hai il permesso di esportare queste schede"
                : "Non hai il permesso di modificare queste schede");
        return t;
    }

    private SchedaAnagrafica schedaDi(TipoAnagrafica t, Long id) {
        return schede.findById(id == null ? -1L : id).filter(s -> s.getTipo().getId().equals(t.getId()))
            .orElseThrow(SchedeService::nonTrovata);
    }

    private TipoVisibile visibile(TipoAnagrafica t, Set<String> azioni, AccessoAnagrafiche.Utente u) {
        DefinizioneAnagrafica def = definizione(t);
        return new TipoVisibile(t.getCodice(), t.getNome(), t.getDescrizione(), t.isAttivo(), t.getVersione(),
            def.senza(def.riservati(u.ruoli(), u.amministratore())), azioni);
    }

    /** Il titolo dal campo titolo (o dal primo campo compilato); se non c'è, il nome dell'anagrafica. */
    private static String titolo(DefinizioneAnagrafica def, TipoAnagrafica t, Map<String, Object> valori) {
        String titolo = def.titolo(valori);
        return titolo != null ? titolo : t.getNome() + " (senza titolo)";
    }

    private static void controllaVersione(SchedaAnagrafica s, Long versione) {
        if (versione == null) throw bad("Indica la versione della scheda che hai letto");
        if (versione != s.getVersione())
            throw conflitto("Qualcun altro ha modificato la scheda nel frattempo: ricaricala e riprova");
    }

    private DefinizioneAnagrafica definizione(TipoAnagrafica t) {
        try {
            return DefinizioneAnagrafica.di(mapper.readTree(t.getDefinizione()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Definizione dell'anagrafica non leggibile", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leggiMappa(String testo) {
        try {
            return mapper.readValue(testo, LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dati della scheda non leggibili", e);
        }
    }

    private String testo(Object valori) {
        try {
            return mapper.writeValueAsString(valori);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nota(String nota) {
        if (nota == null || nota.isBlank()) return null;
        String n = nota.trim();
        if (n.length() > 1000) throw bad("La nota è troppo lunga (al massimo 1000 caratteri)");
        return n;
    }

    private static ResponseStatusException nonTrovata() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Anagrafica o scheda non trovata");
    }

    private static ResponseStatusException conflitto(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
