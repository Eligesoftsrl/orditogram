package com.eligesoft.orditogram.form;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quando una pratica parte (anche da un processo richiamato o dall'API) fissa le versioni dei suoi form: quelle in
 * vigore in quel momento. Da lì in poi un form cambiato nello Studio vale per le pratiche nuove, non per lei.
 *
 * All'avvio di Orditogram, se nessuna pratica ha ancora versioni fissate (primo avvio dopo l'aggiornamento), le
 * fissa per le pratiche già in corso: da quel momento anche loro restano stabili.
 */
@Component
public class VersioniForm implements ActivitiEventListener {

    private static final Logger log = LoggerFactory.getLogger(VersioniForm.class);

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final FormService forms;
    private final PraticaFormRepository fissate;
    private final Map<String, Set<String>> chiaviPerDefinizione = new ConcurrentHashMap<>();

    public VersioniForm(RuntimeService runtimeService, RepositoryService repositoryService, FormService forms,
                        PraticaFormRepository fissate) {
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.forms = forms;
        this.fissate = fissate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registra() {
        fissaPraticheInCorso();
        runtimeService.addEventListener(this, ActivitiEventType.PROCESS_STARTED);
    }

    void fissaPraticheInCorso() {
        if (fissate.count() > 0) return;
        List<ProcessInstance> inCorso = runtimeService.createProcessInstanceQuery().list();
        for (ProcessInstance pi : inCorso) forms.fissa(pi.getId(), chiavi(pi.getProcessDefinitionId()));
        if (!inCorso.isEmpty())
            log.info("Versioni dei form fissate per {} pratiche già in corso", inCorso.size());
    }

    @Override
    public void onEvent(ActivitiEvent event) {
        if (!(event instanceof ActivitiEntityEvent ee) || !(ee.getEntity() instanceof ExecutionEntity e)) return;
        // stessa transazione dell'avvio: se fissare non riesce, la pratica non parte con form incerti
        forms.fissa(e.getProcessInstanceId(), chiavi(e.getProcessDefinitionId()));
    }

    @Override
    public boolean isFailOnException() {
        return true;
    }

    /** I form di un processo: avvio e attività, anche dentro i sottoprocessi. */
    public Set<String> chiavi(String processDefinitionId) {
        // niente computeIfAbsent: leggere il modello può avviare (processi richiamati) e tornare qui,
        // e ConcurrentHashMap rifiuta le modifiche annidate ("Recursive update")
        Set<String> chiavi = chiaviPerDefinizione.get(processDefinitionId);
        if (chiavi == null) {
            chiavi = chiaviDelModello(repositoryService.getBpmnModel(processDefinitionId));
            chiaviPerDefinizione.putIfAbsent(processDefinitionId, chiavi);
        }
        return chiavi;
    }

    public static Set<String> chiaviDelModello(BpmnModel model) {
        Set<String> out = new LinkedHashSet<>();
        if (model == null) return out;
        for (Process p : model.getProcesses()) {
            List<String> chiavi = new ArrayList<>();
            p.findFlowElementsOfType(StartEvent.class, true).forEach(s -> chiavi.add(s.getFormKey()));
            p.findFlowElementsOfType(UserTask.class, true).forEach(t -> chiavi.add(t.getFormKey()));
            chiavi.stream().filter(k -> k != null && !k.isBlank()).forEach(out::add);
        }
        return out;
    }
}
