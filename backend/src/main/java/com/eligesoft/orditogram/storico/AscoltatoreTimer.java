package com.eligesoft.orditogram.storico;

import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.event.ActivitiEntityEvent;
import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.runtime.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Quando scatta un timer (scadenza, sollecito, fine di un'attesa) lo annota nello storico della pratica,
 * così chi la consulta vede perché la pratica ha cambiato strada.
 */
@Component
public class AscoltatoreTimer implements ActivitiEventListener {

    private static final Logger log = LoggerFactory.getLogger(AscoltatoreTimer.class);

    private final RuntimeService runtimeService;
    private final StoricoService storico;
    private final ScadenzeService scadenze;
    private final com.eligesoft.orditogram.glossario.GlossarioService glossario;

    public AscoltatoreTimer(RuntimeService runtimeService, StoricoService storico, ScadenzeService scadenze,
                            com.eligesoft.orditogram.glossario.GlossarioService glossario) {
        this.glossario = glossario;
        this.runtimeService = runtimeService;
        this.storico = storico;
        this.scadenze = scadenze;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registra() {
        runtimeService.addEventListener(this, ActivitiEventType.TIMER_FIRED);
    }

    @Override
    public void onEvent(ActivitiEvent event) {
        try {
            if (!(event instanceof ActivitiEntityEvent ee) || !(ee.getEntity() instanceof Job job)) return;
            if (job.getProcessInstanceId() == null) return;
            FlowElement el = scadenze.elementoDaConfigurazione(job.getProcessDefinitionId(), job.getJobHandlerConfiguration());
            String nome = scadenze.nomeElemento(job.getProcessDefinitionId(), job.getJobHandlerConfiguration());
            String cosa;
            if (el instanceof BoundaryEvent b) {
                cosa = b.isCancelActivity()
                    ? "Termine scaduto: l'attività è stata chiusa e " + glossario.conArticolo(com.eligesoft.orditogram.glossario.Termine.PRATICA, false)
                        + " ha proseguito sulla strada della scadenza"
                    : "Promemoria: l'attività resta aperta";
            } else {
                cosa = "Attesa terminata: " + glossario.conArticolo(com.eligesoft.orditogram.glossario.Termine.PRATICA, false) + " prosegue";
            }
            // transazione separata: un problema dello storico non deve annullare lo scatto del timer
            storico.registraSubito(job.getProcessInstanceId(), PraticaEvento.Tipo.SCADENZA, nome,
                List.of(Map.of("label", "Cosa è successo", "value", cosa)));
        } catch (Exception e) {
            // lo storico non deve mai bloccare il motore
            log.warn("Evento timer non registrato nello storico: {}", e.getMessage());
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }
}
