package com.eligesoft.orditogram.storico;

import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.runtime.Job;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scadenze delle pratiche, lette dai timer programmati nel motore.
 * Ogni timer è legato a un evento del diagramma: un timer sul bordo di un'attività (scadenza o sollecito)
 * oppure un'attesa nel flusso.
 */
@Service
public class ScadenzeService {

    private static final Pattern ACTIVITY_ID = Pattern.compile("\"activityId\"\\s*:\\s*\"([^\"]+)\"");

    /** attivita: id dell'attività a cui è agganciato il timer (null per le attese nel flusso). */
    public record Scadenza(String nome, Date quando, boolean interrompe, String attivita, String elemento, String tipo) {}

    private final ManagementService managementService;
    private final RepositoryService repositoryService;

    public ScadenzeService(ManagementService managementService, RepositoryService repositoryService) {
        this.managementService = managementService;
        this.repositoryService = repositoryService;
    }

    /** Timer attivi di una pratica, dal più vicino. */
    public List<Scadenza> perPratica(String processInstanceId) {
        List<Scadenza> out = new ArrayList<>();
        for (Job job : managementService.createTimerJobQuery().processInstanceId(processInstanceId).list()) {
            String activityId = activityId(job.getJobHandlerConfiguration());
            if (activityId == null || job.getDuedate() == null) continue;
            FlowElement el = elemento(job.getProcessDefinitionId(), activityId);
            String nome = el != null && el.getName() != null && !el.getName().isBlank() ? el.getName() : null;
            if (el instanceof BoundaryEvent b) {
                out.add(new Scadenza(nome != null ? nome : (b.isCancelActivity() ? "Scadenza" : "Sollecito"),
                    job.getDuedate(), b.isCancelActivity(), b.getAttachedToRefId(), activityId,
                    b.isCancelActivity() ? "scadenza" : "sollecito"));
            } else {
                out.add(new Scadenza(nome != null ? nome : "Attesa", job.getDuedate(), false, null, activityId, "attesa"));
            }
        }
        out.sort(Comparator.comparing(Scadenza::quando));
        return out;
    }

    /** Forma pronta per il frontend. */
    public static Map<String, Object> comeMappa(Scadenza s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", s.nome());
        m.put("quando", s.quando());
        m.put("tipo", s.tipo());
        m.put("interrompe", s.interrompe());
        m.put("attivita", s.attivita());
        return m;
    }

    /** Scadenze agganciate a una specifica attività (taskDefinitionKey). */
    public List<Scadenza> perAttivita(String processInstanceId, String taskDefinitionKey) {
        return perPratica(processInstanceId).stream()
            .filter(s -> taskDefinitionKey.equals(s.attivita()))
            .toList();
    }

    /** Nome dell'elemento del diagramma che ha generato un timer (per lo storico). */
    public String nomeElemento(String processDefinitionId, String jobHandlerConfiguration) {
        String id = activityId(jobHandlerConfiguration);
        if (id == null) return "Scadenza";
        FlowElement el = elemento(processDefinitionId, id);
        if (el == null) return "Scadenza";
        if (el.getName() != null && !el.getName().isBlank()) return el.getName();
        if (el instanceof BoundaryEvent b) return b.isCancelActivity() ? "Scadenza" : "Sollecito";
        return "Attesa";
    }

    public FlowElement elementoDaConfigurazione(String processDefinitionId, String jobHandlerConfiguration) {
        String id = activityId(jobHandlerConfiguration);
        return id == null ? null : elemento(processDefinitionId, id);
    }

    private FlowElement elemento(String processDefinitionId, String activityId) {
        Process p = repositoryService.getBpmnModel(processDefinitionId).getMainProcess();
        FlowElement el = p.getFlowElement(activityId);
        if (el != null) return el;
        // dentro un sottoprocesso
        for (SubProcess s : p.findFlowElementsOfType(SubProcess.class, true)) {
            FlowElement f = s.getFlowElement(activityId);
            if (f != null) return f;
        }
        return null;
    }

    static String activityId(String configurazione) {
        if (configurazione == null) return null;
        Matcher m = ACTIVITY_ID.matcher(configurazione);
        if (m.find()) return m.group(1);
        // formato semplice: solo l'id
        return configurazione.startsWith("{") ? null : configurazione;
    }
}
