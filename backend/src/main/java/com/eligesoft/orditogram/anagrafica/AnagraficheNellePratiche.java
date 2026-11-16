package com.eligesoft.orditogram.anagrafica;

import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.user.AppUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Le schede di anagrafica nei form delle pratiche. Un campo { "type": "anagrafica", "tipoAnagrafica": "fondi" }
 * vale l'id della scheda scelta. Quando il form si invia:
 *  - la scheda deve esistere, essere del tipo previsto, attiva e visibile a chi compila (regola "vede" del tipo);
 *    chi non la vede riceve "scheda non trovata", come se non esistesse;
 *  - la pratica si collega alla scheda (pratica_anagrafica) con la fotografia dei dati di quel momento;
 *  - nelle variabili del processo vanno anche &lt;campo&gt;_titolo e &lt;campo&gt;_dati (la fotografia), così BPMN,
 *    gateway ed email usano ${fondo_dati.denominazione}. Dalla fotografia restano fuori i campi riservati.
 */
@Service
public class AnagraficheNellePratiche {

    public static final String SUFFISSO_DATI = "_dati";
    public static final String SUFFISSO_TITOLO = "_titolo";

    /** Una scheda scelta in un campo del form, pronta per essere collegata. */
    public record Scelta(String campo, SchedaAnagrafica scheda, Map<String, Object> fotografia, long versione) { }

    /** Un collegamento come lo mostra la pratica: titolo e dati fotografati, stato attuale solo se visibile. */
    public record Collegamento(String campo, String etichetta, String tipo, String tipoNome, Long id, String titolo,
                               Map<String, Object> fotografia, long versione, OffsetDateTime collegataIl,
                               boolean visibile, String titoloAttuale, String statoAttuale) { }

    private final SchedaAnagraficaRepository schede;
    private final VersioneSchedaRepository versioni;
    private final PraticaAnagraficaRepository collegamenti;
    private final AccessoAnagrafiche accesso;
    private final ObjectMapper mapper;

    public AnagraficheNellePratiche(SchedaAnagraficaRepository schede, VersioneSchedaRepository versioni,
                                    PraticaAnagraficaRepository collegamenti, AccessoAnagrafiche accesso, ObjectMapper mapper) {
        this.schede = schede;
        this.versioni = versioni;
        this.collegamenti = collegamenti;
        this.accesso = accesso;
        this.mapper = mapper;
    }

    /** Il form ha campi anagrafica? */
    public static boolean haCampi(JsonNode form) {
        if (form == null) return false;
        for (JsonNode f : form.path("fields")) if (ValoriForm.ANAGRAFICA.equals(f.path("type").asText())) return true;
        return false;
    }

    /**
     * Le schede scelte nei campi anagrafica compilati, controllate per chi compila. Solleva 400 con il nome del campo
     * se una scheda non va bene.
     */
    @Transactional(readOnly = true)
    public List<Scelta> daForm(JsonNode form, Map<String, Object> valori, AppUser chi) {
        return daForm(form, valori, chi, Map.of());
    }

    /**
     * Come sopra; giaNellaPratica sono i dati che la pratica ha già: un campo che resta sulla stessa scheda non si
     * ricontrolla e non si rifotografa (la scheda potrebbe essere stata cessata nel frattempo).
     */
    @Transactional(readOnly = true)
    public List<Scelta> daForm(JsonNode form, Map<String, Object> valori, AppUser chi, Map<String, Object> giaNellaPratica) {
        if (!haCampi(form)) return new ArrayList<>();
        return daForm(form, valori, accesso.utente(chi), giaNellaPratica);
    }

    /**
     * Come sopra, per chi è già stato riconosciuto (anche un sistema esterno con la sua chiave API). Basta la regola
     * "sceglie" del tipo: chi sceglie non legge i dati della scheda, che vanno solo nella pratica.
     */
    @Transactional(readOnly = true)
    public List<Scelta> daForm(JsonNode form, Map<String, Object> valori, AccessoAnagrafiche.Utente u,
                               Map<String, Object> giaNellaPratica) {
        List<Scelta> out = new ArrayList<>();
        if (!haCampi(form)) return out;
        for (JsonNode f : form.path("fields")) {
            if (!ValoriForm.ANAGRAFICA.equals(f.path("type").asText())) continue;
            String nome = f.path("name").asText();
            Object v = valori.get(nome);
            if (v == null || v.toString().isBlank()) continue;
            Object prima = giaNellaPratica.get(nome);
            if (prima != null && prima.toString().trim().equals(v.toString().trim())) continue;
            String etichetta = f.path("label").asText(nome);
            SchedaAnagrafica s = id(v).flatMap(schede::findById).orElse(null);
            if (s == null || !s.getTipo().getCodice().equals(f.path("tipoAnagrafica").asText())
                || !accesso.puo(u, s.getTipo(), PermessoAnagrafica.SCEGLIE))
                throw new SceltaNonValida(nome, etichetta + ": scheda non trovata");
            if (!s.isAttiva()) throw new SceltaNonValida(nome, etichetta + ": la scheda \"" + s.getTitolo() + "\" è cessata");
            out.add(new Scelta(nome, s, fotografia(s), versioni.countBySchedaId(s.getId())));
        }
        return out;
    }

    /** Le variabili del processo per le schede scelte: &lt;campo&gt;_titolo e &lt;campo&gt;_dati. */
    public static Map<String, Object> variabili(List<Scelta> scelte) {
        Map<String, Object> out = new HashMap<>();
        for (Scelta s : scelte) {
            out.put(s.campo() + SUFFISSO_TITOLO, s.scheda().getTitolo());
            out.put(s.campo() + SUFFISSO_DATI, new LinkedHashMap<>(s.fotografia()));
        }
        return out;
    }

    /** Collega la pratica alle schede scelte (un campo che cambia scheda aggiorna il suo collegamento). */
    @Transactional
    public void registra(String processInstanceId, List<Scelta> scelte, String username) {
        for (Scelta s : scelte) {
            PraticaAnagrafica c = collegamenti.findByProcessInstanceIdAndCampo(processInstanceId, s.campo())
                .orElseGet(PraticaAnagrafica::new);
            c.setProcessInstanceId(processInstanceId);
            c.setCampo(s.campo());
            c.setSchedaId(s.scheda().getId());
            c.setVersioneScheda(s.versione());
            c.setTitolo(s.scheda().getTitolo());
            c.setFotografia(testo(s.fotografia()));
            c.setCollegataIl(OffsetDateTime.now());
            c.setCollegataDa(username);
            collegamenti.save(c);
        }
    }

    /**
     * Le schede collegate alla pratica, per chi la sta guardando (il controllo sulla pratica lo fa chi chiama).
     * Titolo e fotografia sono dati della pratica; il titolo e lo stato attuali della scheda solo per chi può vederla.
     * etichette: nome del campo → etichetta, dai form del processo.
     */
    @Transactional(readOnly = true)
    public List<Collegamento> dellaPratica(String processInstanceId, AppUser chi, Map<String, String> etichette) {
        List<PraticaAnagrafica> righe = collegamenti.findByProcessInstanceIdOrderByCampoAsc(processInstanceId);
        if (righe.isEmpty()) return List.of();
        AccessoAnagrafiche.Utente u = accesso.utente(chi);
        Map<Long, Set<String>> azioni = new HashMap<>();
        List<Collegamento> out = new ArrayList<>();
        for (PraticaAnagrafica r : righe) {
            SchedaAnagrafica s = schede.findById(r.getSchedaId()).orElse(null);
            if (s == null) continue;
            TipoAnagrafica t = s.getTipo();
            boolean visibile = azioni.computeIfAbsent(t.getId(), k -> accesso.azioni(u, t)).contains(PermessoAnagrafica.VEDE);
            out.add(new Collegamento(r.getCampo(), etichette.getOrDefault(r.getCampo(), r.getCampo()), t.getCodice(), t.getNome(),
                s.getId(), r.getTitolo(), leggi(r.getFotografia()), r.getVersioneScheda(), r.getCollegataIl(), visibile,
                visibile ? s.getTitolo() : null, visibile ? s.getStato() : null));
        }
        return out;
    }

    /** I dati della scheda senza i campi riservati e senza quelli tolti dalla definizione. */
    private Map<String, Object> fotografia(SchedaAnagrafica s) {
        DefinizioneAnagrafica def;
        try {
            def = DefinizioneAnagrafica.di(mapper.readTree(s.getTipo().getDefinizione()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Definizione dell'anagrafica non leggibile", e);
        }
        Set<String> riservati = def.riservati(Set.of(), false);
        Map<String, Object> dati = leggi(s.getDati());
        dati.keySet().removeIf(k -> riservati.contains(k) || !def.nomi().contains(k));
        return dati;
    }

    private static Optional<Long> id(Object v) {
        String s = v.toString().trim();
        if (!s.matches("[1-9]\\d{0,17}")) return Optional.empty();
        return Optional.of(Long.valueOf(s));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leggi(String testo) {
        try {
            return mapper.readValue(testo, LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dati della scheda non leggibili", e);
        }
    }

    private String testo(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Una scheda scelta che non va bene: 400, con il campo del form. */
    public static class SceltaNonValida extends ResponseStatusException {
        private final String campo;

        public SceltaNonValida(String campo, String messaggio) {
            super(HttpStatus.BAD_REQUEST, messaggio);
            this.campo = campo;
        }

        public String campo() { return campo; }
    }
}
