package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.form.ValoriForm;
import com.eligesoft.orditogram.messaggio.MessaggiService;
import com.fasterxml.jackson.databind.JsonNode;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * I processi pubblicati descritti per chi integra: i dati da inviare all'avvio (dal form di avvio),
 * se servono allegati, quali messaggi il processo può ricevere e con quali dati.
 * È la base sia dell'API (/api/v1/processi) sia della documentazione OpenAPI generata.
 */
@Service
public class DescrizioneProcessi {

    private final RepositoryService repositoryService;
    private final FormService forms;

    public DescrizioneProcessi(RepositoryService repositoryService, FormService forms) {
        this.repositoryService = repositoryService;
        this.forms = forms;
    }

    /** Ultima versione di ogni processo pubblicato e non sospeso, in ordine di nome. */
    public List<ProcessoPubblicato> pubblicati() {
        List<ProcessoPubblicato> out = new ArrayList<>();
        for (ProcessDefinition d : repositoryService.createProcessDefinitionQuery().latestVersion().list())
            if (!d.isSuspended()) out.add(descrivi(d));
        out.sort(Comparator.comparing(p -> p.nome().toLowerCase(Locale.ROOT)));
        return out;
    }

    public Optional<ProcessoPubblicato> processo(String chiave) {
        if (chiave == null || chiave.isBlank()) return Optional.empty();
        ProcessDefinition d = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(chiave).latestVersion().singleResult();
        return d == null || d.isSuspended() ? Optional.empty() : Optional.of(descrivi(d));
    }

    public ProcessDefinition definizione(String chiave) {
        return repositoryService.createProcessDefinitionQuery().processDefinitionKey(chiave).latestVersion().singleResult();
    }

    /** Il form di avvio della definizione indicata (null se il processo non ne ha). */
    public JsonNode formAvvio(String definizioneId) {
        String fk = repositoryService.getBpmnModel(definizioneId).getMainProcess()
            .findFlowElementsOfType(StartEvent.class).stream()
            .map(StartEvent::getFormKey).filter(Objects::nonNull).findFirst().orElse(null);
        return forms.load(fk);
    }

    private ProcessoPubblicato descrivi(ProcessDefinition d) {
        JsonNode form = formAvvio(d.getId());
        BpmnModel model = repositoryService.getBpmnModel(d.getId());
        List<ProcessoPubblicato.Messaggio> messaggi = MessaggiService.messaggiDichiarati(model).entrySet().stream()
            .map(e -> new ProcessoPubblicato.Messaggio(e.getKey(), List.copyOf(e.getValue()))).toList();
        return new ProcessoPubblicato(d.getKey(), d.getName() != null ? d.getName() : d.getKey(), d.getDescription(),
            d.getVersion(), d.getId(), form != null ? form.path("title").asText(null) : null,
            ValoriForm.campi(form), ValoriForm.allegatiAvvio(form), messaggi);
    }
}
