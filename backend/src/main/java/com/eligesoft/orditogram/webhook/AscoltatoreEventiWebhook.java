package com.eligesoft.orditogram.webhook;

import com.eligesoft.orditogram.user.CurrentUserService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.IdentityLinkEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Traduce gli eventi del motore (pratica avviata/conclusa/annullata, attività creata/assegnata/completata)
 * in eventi per i webhook. Lavora nella transazione del motore: se il passo viene annullato, l'avviso non parte.
 */
@Component
public class AscoltatoreEventiWebhook implements ActivitiEventListener {

    private static final Logger log = LoggerFactory.getLogger(AscoltatoreEventiWebhook.class);

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final WebhookService webhook;
    private final CurrentUserService currentUser;

    public AscoltatoreEventiWebhook(RuntimeService runtimeService, RepositoryService repositoryService,
                                    WebhookService webhook, CurrentUserService currentUser) {
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.webhook = webhook;
        this.currentUser = currentUser;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registra() {
        runtimeService.addEventListener(this,
            ActivitiEventType.PROCESS_STARTED, ActivitiEventType.PROCESS_COMPLETED, ActivitiEventType.PROCESS_CANCELLED,
            ActivitiEventType.TASK_CREATED, ActivitiEventType.TASK_ASSIGNED, ActivitiEventType.TASK_COMPLETED);
    }

    @Override
    public void onEvent(ActivitiEvent event) {
        String tipo = switch (event.getType()) {
            case PROCESS_STARTED -> TipiEvento.PRATICA_AVVIATA;
            case PROCESS_COMPLETED -> TipiEvento.PRATICA_CONCLUSA;
            case PROCESS_CANCELLED -> TipiEvento.PRATICA_ANNULLATA;
            case TASK_CREATED -> TipiEvento.ATTIVITA_CREATA;
            case TASK_ASSIGNED -> TipiEvento.ATTIVITA_ASSEGNATA;
            case TASK_COMPLETED -> TipiEvento.ATTIVITA_COMPLETATA;
            default -> null;
        };
        if (tipo == null || !webhook.interessa(tipo)) return;
        try {
            if (!(event instanceof ActivitiEntityEvent ee)) return;
            Object entita = ee.getEntity();
            if (entita instanceof TaskEntity t) {
                if (t.getProcessInstanceId() == null) return;
                ExecutionEntity pi = t.getProcessInstance();
                webhook.accoda(tipo, pi != null ? pratica(pi) : webhook.pratica(t.getProcessInstanceId()), attivita(t, tipo), null);
            } else if (entita instanceof ExecutionEntity e) {
                ExecutionEntity pi = e.isProcessInstanceType() ? e : e.getProcessInstance();
                if (pi == null || pi.getSuperExecutionId() != null) return;   // sottoprocessi chiamati: non sono pratiche
                List<Map<String, Object>> dettagli = null;
                if (TipiEvento.PRATICA_ANNULLATA.equals(tipo) && pi.getDeleteReason() != null)
                    dettagli = List.of(Map.of("etichetta", "Motivo", "valore", pi.getDeleteReason()));
                webhook.accoda(tipo, pratica(pi), null, dettagli);
            } else if (entita instanceof ProcessInstance p) {
                webhook.accoda(tipo, webhook.pratica(p.getProcessInstanceId()), null, null);
            }
        } catch (RuntimeException e) {
            // un avviso non accodato non deve mai fermare la pratica
            log.warn("Evento {} non accodato per i webhook: {}", tipo, e.getMessage());
        }
    }

    private Map<String, Object> pratica(ExecutionEntity pi) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", pi.getProcessInstanceId());
        m.put("titolo", pi.getName());
        String chiave = pi.getProcessDefinitionKey();
        String nome = null;
        if (pi.getProcessDefinitionId() != null) {
            var def = repositoryService.getProcessDefinition(pi.getProcessDefinitionId());
            chiave = def.getKey();
            nome = def.getName() != null ? def.getName() : def.getKey();
        }
        m.put("processo", chiave);
        m.put("processoNome", nome);
        m.put("riferimento", pi.getBusinessKey());
        return m;
    }

    private Map<String, Object> attivita(TaskEntity t, String tipo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("nome", t.getName());
        m.put("chiave", t.getTaskDefinitionKey());
        m.put("assegnatario", t.getAssignee());
        List<String> gruppi = new ArrayList<>();
        try {
            for (IdentityLinkEntity l : t.getIdentityLinks())
                if ("candidate".equals(l.getType()) && l.getGroupId() != null) gruppi.add(l.getGroupId());
        } catch (RuntimeException ignorata) {
            // a fine attività i collegamenti possono essere già stati rimossi
        }
        m.put("gruppi", gruppi);
        if (TipiEvento.ATTIVITA_COMPLETATA.equals(tipo)) {
            String chi = currentUser.username();
            m.put("completataDa", chi == null || "anonymousUser".equals(chi) ? null : chi);
        }
        return m;
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }
}
