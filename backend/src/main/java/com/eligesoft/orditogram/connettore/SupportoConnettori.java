package com.eligesoft.orditogram.connettore;

import org.activiti.api.process.model.IntegrationContext;
import org.activiti.bpmn.model.*;
import com.eligesoft.orditogram.storico.StoricoService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Funzioni comuni ai connettori: lettura dei parametri dal BPMN e sostituzione dei ${dati}. */
@Component
public class SupportoConnettori {

    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final StoricoService storico;

    public SupportoConnettori(RepositoryService repositoryService, HistoryService historyService,
                              StoricoService storico) {
        this.repositoryService = repositoryService;
        this.historyService = historyService;
        this.storico = storico;
    }

    /**
     * Chi ha avviato la pratica: dai dati della pratica (avviataDaUtente, disponibile anche mentre la pratica
     * sta partendo), dallo storico, oppure dal motore per le pratiche più vecchie.
     */
    public Optional<String> iniziatore(IntegrationContext ctx) {
        Object u = dati(ctx).get("avviataDaUtente");
        if (u != null && !u.toString().isBlank()) return Optional.of(u.toString());
        return iniziatore(ctx.getProcessInstanceId());
    }

    public Optional<String> iniziatore(String processInstanceId) {
        Optional<String> s = storico.iniziatore(processInstanceId);
        if (s.isPresent()) return s;
        HistoricProcessInstance h = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId).singleResult();
        return Optional.ofNullable(h != null ? h.getStartUserId() : null);
    }

    /** I parametri <activiti:field> del service task in esecuzione, letti dal modello BPMN. */
    public Map<String, String> parametri(IntegrationContext ctx) {
        Map<String, String> out = new HashMap<>();
        FlowElement el = repositoryService.getBpmnModel(ctx.getProcessDefinitionId()).getFlowElement(ctx.getClientId());
        if (el instanceof ServiceTask st) {
            for (FieldExtension f : st.getFieldExtensions()) {
                String v = f.getStringValue() != null ? f.getStringValue() : f.getExpression();
                if (v != null) out.put(f.getFieldName(), v);
            }
        }
        return out;
    }

    /**
     * true se un evento di errore sul bordo può intercettare un errore di questa attività:
     * sull'attività stessa o su un sottoprocesso che la contiene.
     */
    public boolean haGestioneErrore(IntegrationContext ctx) {
        BpmnModel model = repositoryService.getBpmnModel(ctx.getProcessDefinitionId());
        FlowElement el = model.getFlowElement(ctx.getClientId());
        List<BoundaryEvent> bordi = new ArrayList<>();
        for (org.activiti.bpmn.model.Process p : model.getProcesses())
            bordi.addAll(p.findFlowElementsOfType(BoundaryEvent.class, true));
        while (el != null) {
            String id = el.getId();
            boolean trovato = bordi.stream().anyMatch(b -> id.equals(b.getAttachedToRefId())
                && b.getEventDefinitions().stream().anyMatch(d -> d instanceof ErrorEventDefinition));
            if (trovato) return true;
            el = el.getSubProcess();
        }
        return false;
    }

    /** Nome dell'attività in esecuzione (per lo storico). */
    public String nomeAttivita(IntegrationContext ctx) {
        FlowElement el = repositoryService.getBpmnModel(ctx.getProcessDefinitionId()).getFlowElement(ctx.getClientId());
        return el != null && el.getName() != null ? el.getName() : ctx.getClientId();
    }

    public static Map<String, Object> dati(IntegrationContext ctx) {
        return ctx.getInBoundVariables() != null ? ctx.getInBoundVariables() : Map.of();
    }

    public static String sostituisci(String testo, Map<String, Object> dati, UnaryOperator<String> escape) {
        return Testi.sostituisci(testo, dati, escape);
    }

    public static String sostituisci(String testo, Map<String, Object> dati) {
        return Testi.sostituisci(testo, dati);
    }

    public static String escapeJson(String s) {
        return Testi.escapeJson(s);
    }
}
