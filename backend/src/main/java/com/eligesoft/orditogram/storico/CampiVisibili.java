package com.eligesoft.orditogram.storico;

import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.accesso.Ruolo;
import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.user.AppUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Quali dati di una pratica vede un utente.
 *  - Personale: tutti, tranne i campi con "ruoliVisibili" se non ha nessuno di quei ruoli (l'amministratore li vede).
 *  - Utenti esterni: solo i campi con "visibileAlleParti" e quelli dei form che hanno compilato loro
 *    (il form di avvio se hanno avviato la pratica, i form delle attività che hanno completato).
 *    Le variabili tecniche, che non sono campi di un form, non le vedono mai.
 */
@Service
public class CampiVisibili {

    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final FormService forms;
    private final PermessiService permessi;
    private final StoricoService storico;

    public CampiVisibili(RepositoryService repositoryService, HistoryService historyService, FormService forms,
                         PermessiService permessi, StoricoService storico) {
        this.repositoryService = repositoryService;
        this.historyService = historyService;
        this.forms = forms;
        this.permessi = permessi;
        this.storico = storico;
    }

    /**
     * I nomi dei dati della pratica che l'utente può vedere. Le variabili ricavate da un campo anagrafica
     * (&lt;campo&gt;_titolo e &lt;campo&gt;_dati, vedi anagrafica/AnagraficheNellePratiche) seguono il campo; agli utenti
     * esterni la fotografia della scheda (_dati) non si mostra mai, al massimo il titolo.
     */
    public Predicate<String> per(AppUser u, String processDefinitionId, String processInstanceId) {
        Predicate<String> campi = perCampi(u, processDefinitionId, processInstanceId);
        if (u == null) return campi;
        Set<String> anagrafiche = new HashSet<>();
        Process p = repositoryService.getBpmnModel(processDefinitionId).getMainProcess();
        List<String> chiavi = new ArrayList<>();
        p.findFlowElementsOfType(StartEvent.class).forEach(s -> { if (s.getFormKey() != null) chiavi.add(s.getFormKey()); });
        p.findFlowElementsOfType(UserTask.class, true).forEach(t -> { if (t.getFormKey() != null) chiavi.add(t.getFormKey()); });
        for (String k : new LinkedHashSet<>(chiavi))
            for (JsonNode f : campi(forms.load(k)))
                if (com.eligesoft.orditogram.form.ValoriForm.ANAGRAFICA.equals(f.path("type").asText())) anagrafiche.add(f.path("name").asText());
        if (anagrafiche.isEmpty()) return campi;
        String dati = com.eligesoft.orditogram.anagrafica.AnagraficheNellePratiche.SUFFISSO_DATI;
        String titolo = com.eligesoft.orditogram.anagrafica.AnagraficheNellePratiche.SUFFISSO_TITOLO;
        boolean esterno = u.isEsterno();
        return nome -> {
            for (String a : anagrafiche) {
                if (nome.equals(a + dati)) return !esterno && campi.test(a);
                if (nome.equals(a + titolo)) return campi.test(a);
            }
            return campi.test(nome);
        };
    }

    private Predicate<String> perCampi(AppUser u, String processDefinitionId, String processInstanceId) {
        if (u == null) return nome -> false;
        Process p = repositoryService.getBpmnModel(processDefinitionId).getMainProcess();
        String avvio = p.findFlowElementsOfType(StartEvent.class).stream().map(StartEvent::getFormKey)
            .filter(Objects::nonNull).findFirst().orElse(null);
        Map<String, JsonNode> perForm = new LinkedHashMap<>();
        if (avvio != null) perForm.put(avvio, forms.load(avvio));
        for (UserTask t : p.findFlowElementsOfType(UserTask.class, true))
            if (t.getFormKey() != null) perForm.computeIfAbsent(t.getFormKey(), forms::load);

        if (!u.isEsterno()) {
            if (permessi.permessiDi(u).containsAll(java.util.EnumSet.allOf(com.eligesoft.orditogram.accesso.Permesso.class))) return nome -> true;
            Set<String> mieiRuoli = permessi.ruoliDi(u).stream().map(Ruolo::getCodice).collect(Collectors.toSet());
            Set<String> riservati = new HashSet<>();
            for (JsonNode form : perForm.values())
                for (JsonNode f : campi(form)) {
                    JsonNode r = f.path("ruoliVisibili");
                    if (!r.isArray() || r.isEmpty()) continue;
                    boolean mio = false;
                    for (JsonNode x : r) mio |= mieiRuoli.contains(x.asText());
                    if (!mio) riservati.add(f.path("name").asText());
                }
            return nome -> !riservati.contains(nome);
        }

        Set<String> visibili = new HashSet<>();
        Set<String> compilati = new HashSet<>();
        if (avvio != null && storico.iniziatore(processInstanceId).filter(u.getUsername()::equals).isPresent())
            compilati.add(avvio);
        for (HistoricTaskInstance t : historyService.createHistoricTaskInstanceQuery().processInstanceId(processInstanceId)
                .taskAssignee(u.getUsername()).finished().list())
            if (t.getFormKey() != null) compilati.add(t.getFormKey());
        perForm.forEach((chiave, form) -> {
            for (JsonNode f : campi(form))
                if (compilati.contains(chiave) || f.path("visibileAlleParti").asBoolean(false)) visibili.add(f.path("name").asText());
        });
        return visibili::contains;
    }

    /** Come sopra, più i campi del form di un'attività che l'utente sta lavorando (servono per compilarla). */
    public Predicate<String> perAttivita(AppUser u, String processDefinitionId, String processInstanceId, JsonNode formAttivita) {
        Set<String> delForm = new HashSet<>();
        for (JsonNode f : campi(formAttivita)) delForm.add(f.path("name").asText());
        return per(u, processDefinitionId, processInstanceId).or(delForm::contains);
    }

    /** Le etichette dei campi che l'utente non vede, per togliere gli stessi valori dallo storico. */
    public Set<String> etichetteNascoste(String processDefinitionId, Predicate<String> visibile) {
        Set<String> out = new HashSet<>();
        Process p = repositoryService.getBpmnModel(processDefinitionId).getMainProcess();
        List<String> chiavi = new ArrayList<>();
        p.findFlowElementsOfType(StartEvent.class).forEach(s -> { if (s.getFormKey() != null) chiavi.add(s.getFormKey()); });
        p.findFlowElementsOfType(UserTask.class, true).forEach(t -> { if (t.getFormKey() != null) chiavi.add(t.getFormKey()); });
        for (String k : chiavi)
            for (JsonNode f : campi(forms.load(k))) {
                String nome = f.path("name").asText();
                if (!visibile.test(nome)) out.add(f.path("label").asText(nome));
            }
        return out;
    }

    private static List<JsonNode> campi(JsonNode form) {
        List<JsonNode> out = new ArrayList<>();
        if (form != null && form.has("fields"))
            for (JsonNode f : form.get("fields")) if (!f.path("name").asText("").isBlank()) out.add(f);
        return out;
    }
}
