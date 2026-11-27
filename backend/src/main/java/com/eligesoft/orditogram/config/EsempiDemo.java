package com.eligesoft.orditogram.config;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Processi di esempio ("Hello World BPMN" e "Pratica"): servono a provare Orditogram, non a un cliente.
 * Con {@code app.demo.processi=true} si pubblicano all'avvio (una sola volta, poi solo se cambiano); con false,
 * il valore predefinito, non esistono. Se un'installazione li ha già (versioni fino alla 0.13 li pubblicavano
 * sempre), si tolgono: si cancellano se non hanno mai avuto pratiche, altrimenti non si possono più avviare e le
 * pratiche già aperte proseguono.
 */
@Component
public class EsempiDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EsempiDemo.class);

    /** Chiavi dei processi di esempio. */
    public static final Set<String> PROCESSI = Set.of("hello", "pratica");
    /** Nome del rilascio degli esempi; "SpringAutoDeployment" è quello con cui li pubblicavano le versioni precedenti. */
    static final String RILASCIO = "Esempi di prova";
    private static final Set<String> RILASCI_ESEMPI = Set.of(RILASCIO, "SpringAutoDeployment");
    private static final String CARTELLA = "classpath:esempi-demo/processi/*";

    private final RepositoryService repository;
    private final HistoryService storia;
    private final boolean attivi;

    public EsempiDemo(RepositoryService repository, HistoryService storia,
                      @Value("${app.demo.processi:false}") boolean attivi) {
        this.repository = repository;
        this.storia = storia;
        this.attivi = attivi;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (attivi) pubblica();
        else togli();
    }

    private void pubblica() throws IOException {
        DeploymentBuilder rilascio = repository.createDeployment().name(RILASCIO).enableDuplicateFiltering();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources(CARTELLA)) {
            try (InputStream in = r.getInputStream()) {
                rilascio.addBytes(r.getFilename(), in.readAllBytes());
            }
        }
        rilascio.deploy();
        for (ProcessDefinition d : definizioniEsempio())
            if (d.isSuspended()) repository.activateProcessDefinitionById(d.getId());
        log.info("Processi di esempio attivi (app.demo.processi=true)");
    }

    private void togli() {
        for (ProcessDefinition d : definizioniEsempio()) {
            Deployment rilascio = repository.createDeploymentQuery().deploymentId(d.getDeploymentId()).singleResult();
            if (rilascio == null || !RILASCI_ESEMPI.contains(rilascio.getName()) || !soloEsempi(rilascio.getId())) continue;
            boolean usato = storia.createHistoricProcessInstanceQuery().processDefinitionId(d.getId()).count() > 0;
            if (!usato && repository.createProcessDefinitionQuery().deploymentId(rilascio.getId()).list().stream()
                    .allMatch(altra -> storia.createHistoricProcessInstanceQuery().processDefinitionId(altra.getId()).count() == 0)) {
                repository.deleteDeployment(rilascio.getId(), false);
                log.info("Processi di esempio tolti (rilascio {})", rilascio.getId());
            } else if (!d.isSuspended()) {
                repository.suspendProcessDefinitionById(d.getId());
                log.info("Processo di esempio {} non più avviabile: ha pratiche, che proseguono", d.getKey());
            }
        }
    }

    /** Il rilascio contiene solo processi di esempio: un processo dell'analista con la stessa chiave resta com'è. */
    private boolean soloEsempi(String rilascio) {
        return repository.createProcessDefinitionQuery().deploymentId(rilascio).list().stream()
            .allMatch(d -> PROCESSI.contains(d.getKey()));
    }

    private List<ProcessDefinition> definizioniEsempio() {
        return repository.createProcessDefinitionQuery().list().stream()
            .filter(d -> PROCESSI.contains(d.getKey())).toList();
    }
}
