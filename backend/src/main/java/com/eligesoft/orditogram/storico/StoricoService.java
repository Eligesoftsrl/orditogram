package com.eligesoft.orditogram.storico;

import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/** Scrive e legge il registro eventi delle pratiche. */
@Service
public class StoricoService {

    public static final String UTENTE_SISTEMA = "orditogram";

    private final PraticaEventoRepository repo;
    private final CurrentUserService currentUser;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher eventi;
    private final com.eligesoft.orditogram.accesso.OrganizzazioneRepository organizzazioni;

    public StoricoService(PraticaEventoRepository repo, CurrentUserService currentUser, ObjectMapper mapper,
                          ApplicationEventPublisher eventi,
                          com.eligesoft.orditogram.accesso.OrganizzazioneRepository organizzazioni) {
        this.organizzazioni = organizzazioni;
        this.repo = repo;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.eventi = eventi;
    }

    /** Registra un evento a nome dell'utente corrente. dettagli: lista di {label, value}. */
    @Transactional
    public void registra(String processInstanceId, PraticaEvento.Tipo tipo, String titolo,
                         List<Map<String, Object>> dettagli) {
        // azioni automatiche (scadenze, timer) non hanno un utente: le registra "Orditogram"
        String username = currentUser.username();
        boolean sistema = username == null || "anonymousUser".equals(username);
        PraticaEvento e = new PraticaEvento();
        e.setProcessInstanceId(processInstanceId);
        e.setTipo(tipo);
        e.setTitolo(titolo);
        e.setUtente(sistema ? UTENTE_SISTEMA : username);
        e.setUtenteNome(sistema ? "Orditogram (automatico)" : currentUser.fullNameOf(username));
        e.setQuando(OffsetDateTime.now());
        try {
            e.setDettagli(dettagli == null || dettagli.isEmpty() ? null : mapper.writeValueAsString(dettagli));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        repo.save(e);
        // chi ascolta (i webhook) lavora nella stessa transazione: se l'operazione viene annullata, niente avviso
        eventi.publishEvent(new EventoStorico(processInstanceId, tipo, titolo, e.getUtente(), e.getUtenteNome(),
            dettagli == null ? List.of() : List.copyOf(dettagli)));
    }

    /**
     * Come registra, ma in una transazione separata: l'evento resta anche se l'operazione in corso
     * viene annullata (es. un servizio esterno non risponde e il completamento dell'attività fallisce).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registraSubito(String processInstanceId, PraticaEvento.Tipo tipo, String titolo,
                               List<Map<String, Object>> dettagli) {
        registra(processInstanceId, tipo, titolo, dettagli);
    }

    /** Eventi della pratica in ordine cronologico, pronti per il frontend. */
    public List<Map<String, Object>> eventi(String processInstanceId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PraticaEvento e : repo.findByProcessInstanceIdOrderByQuandoAscIdAsc(processInstanceId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("tipo", e.getTipo().name());
            m.put("titolo", e.getTitolo());
            m.put("utente", e.getUtente());
            m.put("utenteNome", e.getUtenteNome());
            m.put("quando", e.getQuando());
            m.put("dettagli", parse(e.getDettagli()));
            out.add(m);
        }
        return out;
    }

    /** Username di chi ha avviato la pratica (se registrato). */
    public Optional<String> iniziatore(String processInstanceId) {
        return repo.findFirstByProcessInstanceIdAndTipoOrderByIdAsc(processInstanceId, PraticaEvento.Tipo.AVVIO)
            .map(PraticaEvento::getUtente);
    }

    public boolean haPartecipato(String processInstanceId, String username) {
        return repo.existsByProcessInstanceIdAndUtente(processInstanceId, username);
    }

    public Set<String> praticheDi(String username) {
        return repo.findPraticheDiUtente(username);
    }

    /**
     * Dai valori inviati con un form ricava i dettagli leggibili per lo storico:
     * solo i campi modificabili, con la loro etichetta. Checkbox → Sì/No.
     */
    public List<Map<String, Object>> dettagliDaForm(JsonNode form, Map<String, Object> values) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (form == null || values == null || !form.has("fields")) return out;
        for (JsonNode f : form.get("fields")) {
            if (f.path("readonly").asBoolean(false)) continue;
            String name = f.path("name").asText();
            if (!values.containsKey(name)) continue;
            Object v = values.get(name);
            String text;
            if ("checkbox".equals(f.path("type").asText())) text = Boolean.TRUE.equals(v) ? "Sì" : "No";
            else if (com.eligesoft.orditogram.form.ValoriForm.ORGANIZZAZIONE.equals(f.path("type").asText()) && v != null
                && v.toString().matches("\\d{1,18}"))
                text = organizzazioni.findById(Long.valueOf(v.toString())).map(o -> o.getNome()).orElse(v.toString());
            else if (v instanceof List<?> righe) text = righe.isEmpty() ? "" : righe.size() + (righe.size() == 1 ? " riga" : " righe");
            else text = v == null ? "" : v.toString().trim();
            if (text.isEmpty()) continue;
            out.add(Map.of("label", f.path("label").asText(name), "value", text));
        }
        return out;
    }

    private List<Map<String, Object>> parse(String json) {
        if (json == null) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
