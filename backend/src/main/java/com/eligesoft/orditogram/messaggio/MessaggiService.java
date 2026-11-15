package com.eligesoft.orditogram.messaggio;

import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.StoricoService;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ExecutionQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Messaggi in arrivo: un evento "messaggio" nel BPMN mette la pratica in attesa (nel flusso) o la fa deviare
 * (sul bordo di un'attività) finché un sistema esterno, o un operatore, non segnala l'arrivo.
 */
@Service
public class MessaggiService {

    private static final Pattern NOME = Pattern.compile("[a-zA-Z][\\w-]*");
    private static final int MAX_DATI = 50;
    private static final int MAX_LUNGHEZZA = 10_000;

    /** Un messaggio che una pratica sta aspettando. */
    public record Atteso(String nome, String elemento, String tipo, String attivita) {}

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final StoricoService storico;

    public MessaggiService(RuntimeService runtimeService, RepositoryService repositoryService,
                           HistoryService historyService, StoricoService storico) {
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
        this.storico = storico;
    }

    /** Messaggi attesi ora da una pratica in corso. */
    public List<Atteso> attesi(String processInstanceId) {
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (h == null || h.getEndTime() != null) return List.of();
        return attesi(processInstanceId, h.getProcessDefinitionId());
    }

    /** Come sopra, per una pratica in corso di cui si conosce già la definizione. */
    public List<Atteso> attesi(String processInstanceId, String processDefinitionId) {
        BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
        List<Atteso> out = new ArrayList<>();
        Set<String> visti = new HashSet<>();
        for (Process p : model.getProcesses()) {
            for (Event ev : eventiMessaggio(p)) {
                String nome = nomeMessaggio(model, ev);
                if (nome == null || !visti.add(nome + "|" + ev.getId())) continue;
                // l'esecuzione in attesa del messaggio è quella dell'evento stesso (anche sul bordo)
                boolean attende = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId).activityId(ev.getId()).messageEventSubscriptionName(nome).count() > 0;
                if (!attende) continue;
                String etichetta = ev.getName() != null && !ev.getName().isBlank() ? ev.getName() : nome;
                if (ev instanceof BoundaryEvent b) out.add(new Atteso(nome, etichetta, "bordo", b.getAttachedToRefId()));
                else out.add(new Atteso(nome, etichetta, "attesa", null));
            }
        }
        return out;
    }

    /**
     * Consegna il messaggio alle pratiche che lo aspettano.
     * pratica: id della pratica; oppure cerca: { dato: valore } per trovarla dai suoi dati (es. numeroProtocollo).
     * Restituisce gli id delle pratiche che lo hanno ricevuto.
     */
    @Transactional
    public List<String> consegna(String nome, String pratica, Map<String, Object> cerca, Map<String, Object> dati,
                                 String mittente) {
        return consegna(nome, pratica, cerca, dati, mittente, Set.of());
    }

    /** Come sopra, limitata alle pratiche dei processi indicati (vuoto = tutti): per le chiavi API ristrette. */
    @Transactional
    public List<String> consegna(String nome, String pratica, Map<String, Object> cerca, Map<String, Object> dati,
                                 String mittente, Set<String> processiConsentiti) {
        if (nome == null || !NOME.matcher(nome).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome del messaggio non valido");
        Map<String, Object> valori = pulisci(dati);

        ExecutionQuery q = runtimeService.createExecutionQuery().messageEventSubscriptionName(nome);
        if (processiConsentiti != null && !processiConsentiti.isEmpty()) q.processDefinitionKeys(processiConsentiti);
        if (pratica != null && !pratica.isBlank()) q.processInstanceId(pratica);
        else if (cerca != null && !cerca.isEmpty()) {
            for (var e : cerca.entrySet()) {
                if (!NOME.matcher(e.getKey()).matches() || e.getValue() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Criterio di ricerca non valido: " + e.getKey());
                q.processVariableValueEquals(e.getKey(), valoreDiRicerca(e.getValue()));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la pratica (\"pratica\") o come trovarla (\"cerca\")");
        }

        List<Execution> esecuzioni = q.list();
        if (esecuzioni.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nessuna pratica sta aspettando il messaggio \"" + nome + "\"");
        Set<String> pratiche = new LinkedHashSet<>();
        esecuzioni.forEach(e -> pratiche.add(e.getProcessInstanceId()));
        if (pratiche.size() > 1 && (pratica == null || pratica.isBlank()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Il criterio indicato corrisponde a " + pratiche.size() + " pratiche: rendilo più preciso");

        // si accettano solo i dati che il messaggio dichiara nel BPMN (<orditogram:datiMessaggio>):
        // un sistema esterno non può cambiare altri dati della pratica
        for (Execution e : esecuzioni) {
            Set<String> ammessi = datiAmmessi(e.getProcessInstanceId(), nome);
            for (String k : valori.keySet())
                if (!ammessi.contains(k))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Il dato \"" + k + "\" non è previsto dal messaggio \"" + nome + "\""
                            + (ammessi.isEmpty() ? " (il messaggio non porta dati)" : " (previsti: " + String.join(", ", ammessi) + ")"));
        }

        // la storia prima della consegna: se la pratica prosegue con altri passi l'ordine resta corretto
        for (String pi : pratiche) {
            List<Map<String, Object>> dettagli = new ArrayList<>();
            dettagli.add(Map.of("label", "Da", "value", mittente));
            valori.forEach((k, v) -> dettagli.add(Map.of("label", k, "value", String.valueOf(v))));
            storico.registra(pi, PraticaEvento.Tipo.MESSAGGIO, "Messaggio ricevuto: " + nome, dettagli);
        }
        // i dati diventano dati della pratica, poi il messaggio fa proseguire l'attesa
        for (String pi : pratiche) if (!valori.isEmpty()) runtimeService.setVariables(pi, valori);
        for (Execution e : esecuzioni) {
            // una consegna precedente (es. un evento sul bordo che chiude l'attività) può aver chiuso questa attesa
            boolean ancoraInAttesa = runtimeService.createExecutionQuery().executionId(e.getId())
                .messageEventSubscriptionName(nome).count() > 0;
            if (ancoraInAttesa) runtimeService.messageEventReceived(nome, e.getId());
        }
        return new ArrayList<>(pratiche);
    }

    /** Dati dichiarati dagli eventi che aspettano questo messaggio nel processo della pratica. */
    private Set<String> datiAmmessi(String processInstanceId, String nome) {
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (h == null) return Set.of();
        BpmnModel model = repositoryService.getBpmnModel(h.getProcessDefinitionId());
        Set<String> out = new TreeSet<>();
        for (Process p : model.getProcesses())
            for (Event ev : eventiMessaggio(p))
                if (nome.equals(nomeMessaggio(model, ev)))
                    for (ExtensionElement x : ev.getExtensionElements().getOrDefault("datiMessaggio", List.of()))
                        if (x.getElementText() != null)
                            for (String d : x.getElementText().split("[,\\s]+"))
                                if (NOME.matcher(d).matches()) out.add(d);
        return out;
    }

    /** Per la ricerca: i numeri interi come Long (come li salva il motore), testo e sì/no così come sono. */
    private static Object valoreDiRicerca(Object v) {
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Short sh) return sh.longValue();
        if (v instanceof Boolean || v instanceof Number) return v;
        return v.toString();
    }

    /** Solo dati semplici, con nomi validi e dimensioni ragionevoli. */
    private static Map<String, Object> pulisci(Map<String, Object> dati) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (dati == null) return out;
        if (dati.size() > MAX_DATI) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Troppi dati nel messaggio");
        for (var e : dati.entrySet()) {
            if (!NOME.matcher(e.getKey()).matches())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome di dato non valido: " + e.getKey());
            Object v = e.getValue();
            if (v == null) continue;
            if (v instanceof Boolean || v instanceof Number) out.put(e.getKey(), v);
            else if (v instanceof String s) {
                if (s.length() > MAX_LUNGHEZZA) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valore troppo lungo: " + e.getKey());
                out.put(e.getKey(), s);
            } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il dato " + e.getKey() + " deve essere testo, numero o sì/no");
        }
        return out;
    }

    /** Messaggi che il processo può ricevere, con i dati che ciascuno porta (per l'API e la sua documentazione). */
    public static Map<String, Set<String>> messaggiDichiarati(BpmnModel model) {
        Map<String, Set<String>> out = new TreeMap<>();
        for (Process p : model.getProcesses())
            for (Event ev : eventiMessaggio(p)) {
                String nome = nomeMessaggio(model, ev);
                if (nome == null || !NOME.matcher(nome).matches()) continue;
                Set<String> dati = out.computeIfAbsent(nome, k -> new TreeSet<>());
                for (ExtensionElement x : ev.getExtensionElements().getOrDefault("datiMessaggio", List.of()))
                    if (x.getElementText() != null)
                        for (String d : x.getElementText().split("[,\\s]+"))
                            if (NOME.matcher(d).matches()) dati.add(d);
            }
        return out;
    }

    static List<Event> eventiMessaggio(Process p) {
        List<Event> out = new ArrayList<>();
        for (FlowElement el : p.findFlowElementsOfType(FlowElement.class, true))
            if ((el instanceof IntermediateCatchEvent || el instanceof BoundaryEvent)
                && ((Event) el).getEventDefinitions().stream().anyMatch(d -> d instanceof MessageEventDefinition))
                out.add((Event) el);
        return out;
    }

    /** Il riferimento può essere l'id del messaggio o (dopo la pubblicazione) già il suo nome. */
    static String nomeMessaggio(BpmnModel model, Event ev) {
        for (EventDefinition d : ev.getEventDefinitions()) {
            if (d instanceof MessageEventDefinition m && m.getMessageRef() != null) {
                String ref = m.getMessageRef();
                if (model.containsMessageId(ref)) {
                    Message msg = model.getMessage(ref);
                    return msg.getName() != null && !msg.getName().isBlank() ? msg.getName() : ref;
                }
                return ref;
            }
        }
        return null;
    }
}
