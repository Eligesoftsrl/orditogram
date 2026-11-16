package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.user.AppUser;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.IdentityLinkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Attività assegnate a una parte della pratica. Nel BPMN l'analista scrive candidateGroups="parte:controparte";
 * quando l'attività nasce, il gruppo diventa quello dell'organizzazione della parte ("org:12") oppure la persona
 * (candidato), letti dalla variabile "parte_controparte" (vedi PartiService). Se la parte non c'è ancora,
 * l'attività resta al gruppo "parte:controparte", che nessuno ha: non finisce nelle mani sbagliate.
 */
@Component
public class AscoltatoreParti implements ActivitiEventListener {

    private static final Logger log = LoggerFactory.getLogger(AscoltatoreParti.class);

    private final RuntimeService runtimeService;

    public AscoltatoreParti(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registra() {
        runtimeService.addEventListener(this, ActivitiEventType.TASK_CREATED);
    }

    @Override
    public void onEvent(ActivitiEvent event) {
        if (!(event instanceof ActivitiEntityEvent ee) || !(ee.getEntity() instanceof TaskEntity task)) return;
        List<String> daRisolvere = new ArrayList<>();
        for (IdentityLink l : task.getIdentityLinks())
            if (IdentityLinkType.CANDIDATE.equals(l.getType()) && l.getGroupId() != null
                && l.getGroupId().startsWith(PartiService.PREFISSO_GRUPPO)) daRisolvere.add(l.getGroupId());
        for (String gruppo : daRisolvere) {
            String ruolo = gruppo.substring(PartiService.PREFISSO_GRUPPO.length());
            Object rif = task.getVariable(PartiService.PREFISSO_VARIABILE + ruolo);
            String s = rif == null ? "" : rif.toString();
            if (s.startsWith(PartiService.RIF_UTENTE)) {
                task.addCandidateUser(s.substring(PartiService.RIF_UTENTE.length()));
            } else if (s.startsWith(PartiService.RIF_ORG)) {
                task.addCandidateGroup(AppUser.PREFISSO_ORG + s.substring(PartiService.RIF_ORG.length()));
            } else {
                log.warn("Attività {} della pratica {}: la parte \"{}\" non è ancora indicata", task.getName(),
                    task.getProcessInstanceId(), ruolo);
                continue;
            }
            task.deleteCandidateGroup(gruppo);
        }
    }

    /** Un errore qui deve fermare l'operazione: un'attività assegnata male è peggio di un errore visibile. */
    @Override
    public boolean isFailOnException() {
        return true;
    }
}
