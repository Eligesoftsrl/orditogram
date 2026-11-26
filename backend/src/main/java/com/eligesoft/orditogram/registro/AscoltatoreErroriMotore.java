package com.eligesoft.orditogram.registro;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.delegate.event.ActivitiExceptionEvent;
import org.activiti.engine.runtime.Job;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * I lavori del motore che falliscono (timer, attese, service task asincroni come i connettori) finiscono nel
 * registro degli errori: nessuno li vede altrimenti, perché non c'è una persona che aspetta la risposta.
 */
@Component
public class AscoltatoreErroriMotore implements ActivitiEventListener {

    private final RuntimeService runtimeService;
    private final RegistroErrori errori;

    public AscoltatoreErroriMotore(RuntimeService runtimeService, RegistroErrori errori) {
        this.runtimeService = runtimeService;
        this.errori = errori;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registra() {
        runtimeService.addEventListener(this, ActivitiEventType.JOB_EXECUTION_FAILURE);
    }

    @Override
    public void onEvent(ActivitiEvent event) {
        Throwable causa = event instanceof ActivitiExceptionEvent ee ? ee.getCause() : null;
        if (causa == null) return;
        String descrizione = "lavoro del motore";
        if (event instanceof ActivitiEntityEvent e && e.getEntity() instanceof Job job)
            descrizione = "lavoro " + job.getId() + (job.getProcessInstanceId() != null ? " pratica " + job.getProcessInstanceId() : "");
        errori.registra(RegistroErrore.MOTORE, causa, descrizione);
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }
}
