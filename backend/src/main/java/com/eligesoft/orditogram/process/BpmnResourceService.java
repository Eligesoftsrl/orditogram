package com.eligesoft.orditogram.process;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Lettura e parsing dei file BPMN (deployati o caricati). */
@Service
public class BpmnResourceService {

    private final RepositoryService repositoryService;

    public BpmnResourceService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /** XML del BPMN di una definizione deployata. */
    public String xml(String processDefinitionId) {
        ProcessDefinition def = repositoryService.getProcessDefinition(processDefinitionId);
        try (InputStream in = repositoryService.getResourceAsStream(def.getDeploymentId(), def.getResourceName())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("BPMN non leggibile: " + processDefinitionId, e);
        }
    }

    /** Converte i byte di un file .bpmn in modello Activiti (con protezione XXE). */
    public BpmnModel parse(byte[] bytes) {
        try {
            XMLInputFactory xif = XMLInputFactory.newInstance();
            xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader xtr = xif.createXMLStreamReader(new ByteArrayInputStream(bytes), "UTF-8");
            return new BpmnXMLConverter().convertToBpmnModel(xtr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Il file non è un BPMN valido: " + e.getMessage());
        }
    }
}
