package com.eligesoft.orditogram.storico;

import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.api.task.model.builders.TaskPayloadBuilder;
import org.activiti.api.task.runtime.TaskRuntime;
import org.activiti.engine.TaskService;
import org.activiti.engine.task.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Chi può vedere una pratica e chi può modificarne i documenti.
 *  - vedere:    admin, chi ha partecipato (ha un evento nello storico),
 *               chi ha un task della pratica in carico o disponibile per i suoi gruppi
 *  - modificare i documenti: chi ha in carico un task della pratica il cui form ha "documenti": "modifica"
 */
@Service
public class PraticaAccessService {

    private final StoricoService storico;
    private final CurrentUserService currentUser;
    private final TaskRuntime taskRuntime;
    private final TaskService taskService;
    private final FormService forms;

    public PraticaAccessService(StoricoService storico, CurrentUserService currentUser,
                                TaskRuntime taskRuntime, TaskService taskService, FormService forms) {
        this.storico = storico;
        this.currentUser = currentUser;
        this.taskRuntime = taskRuntime;
        this.taskService = taskService;
        this.forms = forms;
    }

    public boolean canView(String processInstanceId) {
        if (currentUser.isAdmin()) return true;
        if (storico.haPartecipato(processInstanceId, currentUser.username())) return true;
        // TaskRuntime restituisce solo i task assegnati all'utente o candidati per i suoi gruppi
        return taskRuntime.tasks(Pageable.of(0, 1),
                TaskPayloadBuilder.tasks().withProcessInstanceId(processInstanceId).build())
            .getTotalItems() > 0;
    }

    public void checkView(String processInstanceId) {
        if (!canView(processInstanceId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non hai accesso a questa pratica");
    }

    /** Il task in carico all'utente che gli permette di modificare i documenti, se c'è. */
    public Optional<Task> taskConModificaDocumenti(String processInstanceId) {
        return taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .taskAssignee(currentUser.username())
            .list().stream()
            .filter(t -> {
                var form = forms.load(t.getFormKey());
                return form != null && "modifica".equals(form.path("documenti").asText());
            })
            .findFirst();
    }

    /** Un task libero del gruppo dell'utente che consente di modificare i documenti. */
    public Optional<Task> taskLiberoConModificaDocumenti(String processInstanceId) {
        var gruppi = new java.util.ArrayList<>(currentUser.groups());
        var query = taskService.createTaskQuery().processInstanceId(processInstanceId);
        query = gruppi.isEmpty() ? query.taskCandidateUser(currentUser.username())
                                 : query.taskCandidateUser(currentUser.username(), gruppi);
        return query.list().stream()
            .filter(t -> t.getAssignee() == null)
            .filter(t -> {
                var form = forms.load(t.getFormKey());
                return form != null && "modifica".equals(form.path("documenti").asText());
            })
            .findFirst();
    }

    /** L'utente può modificare i documenti: ha in carico un task che lo consente, o può prenderne uno. */
    public boolean puoModificareDocumenti(String processInstanceId) {
        return taskConModificaDocumenti(processInstanceId).isPresent()
            || taskLiberoConModificaDocumenti(processInstanceId).isPresent();
    }

    /**
     * Task che consente di modificare i documenti. Se l'utente non ne ha in carico uno ma può lavorarne uno
     * libero del suo gruppo, lo prende in carico ora: caricare un documento è già lavorare l'attività.
     */
    public Task checkEditDocumenti(String processInstanceId) {
        Optional<Task> mio = taskConModificaDocumenti(processInstanceId);
        if (mio.isPresent()) return mio.get();
        Optional<Task> libero = taskLiberoConModificaDocumenti(processInstanceId);
        if (libero.isPresent()) {
            taskRuntime.claim(TaskPayloadBuilder.claim().withTaskId(libero.get().getId()).build());
            storico.registra(processInstanceId, PraticaEvento.Tipo.PRESA_IN_CARICO, libero.get().getName(), null);
            return taskService.createTaskQuery().taskId(libero.get().getId()).singleResult();
        }
        return taskConModificaDocumenti(processInstanceId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Per modificare i documenti devi avere in carico un task della pratica che lo consente"));
    }
}
