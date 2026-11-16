package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.StoricoService;
import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Le parti di una pratica (vedi PraticaParte). Si aggiungono:
 *  - in automatico, quando un utente esterno avvia la pratica: è il "richiedente";
 *  - da un campo Organizzazione di un form marcato come parte ("parte": "controparte").
 * Per il motore ogni parte è anche una variabile "parte_controparte" = "org:12" oppure "utente:mario":
 * AscoltatoreParti la usa per assegnare le attività con candidateGroups="parte:controparte".
 */
@Service
public class PartiService {

    /** Gruppo candidato di un'attività da assegnare a una parte: "parte:controparte". */
    public static final String PREFISSO_GRUPPO = "parte:";
    /** Variabile della pratica con il riferimento alla parte: "parte_controparte". */
    public static final String PREFISSO_VARIABILE = "parte_";
    public static final String RIF_ORG = "org:";
    public static final String RIF_UTENTE = "utente:";
    /** Nome del ruolo di una parte: diventa anche parte del nome di una variabile. */
    public static final Pattern RUOLO = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,59}");

    /** Una parte da aggiungere o aggiornare. */
    public record Parte(String ruolo, String utente, Long organizzazioneId) {
        /** Riferimento per il motore: la persona se c'è, altrimenti l'organizzazione. */
        public String riferimento() {
            return utente != null ? RIF_UTENTE + utente : RIF_ORG + organizzazioneId;
        }
    }

    private final PraticaParteRepository parti;
    private final OrganizzazioneRepository organizzazioni;
    private final StoricoService storico;
    private final CurrentUserService currentUser;

    public PartiService(PraticaParteRepository parti, OrganizzazioneRepository organizzazioni,
                        StoricoService storico, CurrentUserService currentUser) {
        this.parti = parti;
        this.organizzazioni = organizzazioni;
        this.storico = storico;
        this.currentUser = currentUser;
    }

    /**
     * Controlla i campi Organizzazione compilati (un'organizzazione esterna attiva) e restituisce le parti che
     * indicano. Un valore non valido è un errore 400: nessuno può collegare la pratica a un'organizzazione a caso.
     */
    @Transactional(readOnly = true)
    public List<Parte> daForm(JsonNode form, Map<String, Object> valori) {
        List<Parte> out = new ArrayList<>();
        if (form == null || !form.has("fields") || valori == null) return out;
        for (JsonNode f : form.get("fields")) {
            if (!ValoriForm.ORGANIZZAZIONE.equals(f.path("type").asText())) continue;
            String nome = f.path("name").asText();
            Object v = valori.get(nome);
            if (v == null || v.toString().isBlank()) continue;
            Organizzazione o = esternaAttiva(v.toString())
                .orElseThrow(() -> bad("Scegli un'organizzazione dall'elenco per \"" + f.path("label").asText(nome) + "\""));
            String ruolo = f.path("parte").asText("").trim();
            if (!ruolo.isEmpty() && RUOLO.matcher(ruolo).matches()) out.add(new Parte(ruolo, null, o.getId()));
        }
        return out;
    }

    /** Il richiedente, se chi avvia è un utente esterno: lui, con la sua organizzazione se ne ha una sola. */
    public Optional<Parte> richiedente(AppUser u) {
        if (u == null || !u.isEsterno()) return Optional.empty();
        Long org = u.getOrganizzazioni().size() == 1 ? u.getOrganizzazioni().iterator().next() : null;
        return Optional.of(new Parte(PraticaParte.RICHIEDENTE, u.getUsername(), org));
    }

    /** Le variabili da mettere nella pratica perché il motore sappia chi sono le parti. */
    public static Map<String, Object> variabili(Collection<Parte> nuove) {
        Map<String, Object> out = new HashMap<>();
        nuove.forEach(p -> out.put(PREFISSO_VARIABILE + p.ruolo(), p.riferimento()));
        return out;
    }

    /** Salva le parti (una per ruolo: la nuova sostituisce la vecchia) e le annota nello storico. */
    @Transactional
    public void registra(String processInstanceId, Collection<Parte> nuove) {
        for (Parte p : nuove) {
            PraticaParte esistente = parti.findByProcessInstanceIdAndRuolo(processInstanceId, p.ruolo()).orElse(null);
            if (esistente != null && Objects.equals(esistente.getUtente(), p.utente())
                && Objects.equals(esistente.getOrganizzazioneId(), p.organizzazioneId())) continue;
            PraticaParte x = esistente != null ? esistente : new PraticaParte();
            x.setProcessInstanceId(processInstanceId);
            x.setRuolo(p.ruolo());
            x.setUtente(p.utente());
            x.setOrganizzazioneId(p.organizzazioneId());
            x.setAggiuntaIl(OffsetDateTime.now());
            x.setAggiuntaDa(currentUser.username());
            parti.save(x);
            storico.registra(processInstanceId, PraticaEvento.Tipo.PARTE,
                (esistente == null ? "Parte aggiunta: " : "Parte cambiata: ") + etichetta(p.ruolo()), descrivi(x));
        }
    }

    /** Le parti della pratica, per il dettaglio. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> elenco(String processInstanceId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PraticaParte p : parti.findByProcessInstanceIdOrderByIdAsc(processInstanceId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruolo", p.getRuolo());
            m.put("etichetta", etichetta(p.getRuolo()));
            m.put("utente", p.getUtente() == null ? null : currentUser.fullNameOf(p.getUtente()));
            m.put("organizzazione", p.getOrganizzazioneId() == null ? null
                : organizzazioni.findById(p.getOrganizzazioneId()).map(Organizzazione::getNome).orElse(null));
            out.add(m);
        }
        return out;
    }

    /** "controparte" → "Controparte", "ente_gestore" → "Ente gestore". */
    public static String etichetta(String ruolo) {
        if (ruolo == null || ruolo.isEmpty()) return "";
        String s = ruolo.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** L'organizzazione esterna attiva con questo id (il valore di un campo Organizzazione). */
    @Transactional(readOnly = true)
    public Optional<Organizzazione> esternaAttiva(String id) {
        try {
            return organizzazioni.findById(Long.valueOf(id.trim())).filter(o -> o.isEsterna() && o.isAttiva());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<Map<String, Object>> descrivi(PraticaParte p) {
        List<Map<String, Object>> d = new ArrayList<>();
        if (p.getUtente() != null) d.add(Map.of("label", "Persona", "value", currentUser.fullNameOf(p.getUtente())));
        if (p.getOrganizzazioneId() != null)
            organizzazioni.findById(p.getOrganizzazioneId())
                .ifPresent(o -> d.add(Map.of("label", "Organizzazione", "value", o.getNome())));
        return d;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
