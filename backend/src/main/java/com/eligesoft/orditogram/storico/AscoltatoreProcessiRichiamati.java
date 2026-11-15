package com.eligesoft.orditogram.storico;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Quando un processo richiamato (call activity) apre una pratica figlia, le dà il titolo della pratica madre:
 * così nella scrivania e nell'elenco delle pratiche si riconosce a cosa si riferisce.
 */
@Component
public class AscoltatoreProcessiRichiamati implements ActivitiEventListener {

    private static final Logger log = LoggerFactory.getLogger(AscoltatoreProcessiRichiamati.class);

    private final RuntimeService runtimeService;

    public AscoltatoreProcessiRichiamati(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registra() {
        runtimeService.addEventListener(this, ActivitiEventType.PROCESS_STARTED);
    }

    @Override
    public void onEvent(ActivitiEvent event) {
        try {
            if (!(event instanceof ActivitiEntityEvent ee) || !(ee.getEntity() instanceof ExecutionEntity figlia)) return;
            ExecutionEntity chiamante = figlia.getSuperExecution();
            if (chiamante == null || figlia.getName() != null) return;
            ExecutionEntity madre = chiamante.getProcessInstance();
            if (madre != null && madre.getName() != null) figlia.setName(madre.getName());
        } catch (Exception e) {
            // il titolo è una comodità: non deve mai bloccare il motore
            log.warn("Titolo della pratica richiamata non impostato: {}", e.getMessage());
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }
}
