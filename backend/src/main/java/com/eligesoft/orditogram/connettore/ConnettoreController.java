package com.eligesoft.orditogram.connettore;

import com.eligesoft.orditogram.user.AppUserRepository;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** Dati di supporto all'editor dei processi (solo admin, sotto /api/admin). */
@RestController
@RequestMapping("/api/admin")
public class ConnettoreController {

    private final ApplicationContext ctx;
    private final AppUserRepository users;
    private final RepositoryService repositoryService;

    public ConnettoreController(ApplicationContext ctx, AppUserRepository users, RepositoryService repositoryService) {
        this.ctx = ctx;
        this.users = users;
        this.repositoryService = repositoryService;
    }

    /** Catalogo dei connettori disponibili per le attività automatiche. */
    @GetMapping("/connettori")
    public List<Map<String, Object>> connettori() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConnettoreOrditogram c : ctx.getBeansOfType(ConnettoreOrditogram.class).values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("nome", c.nome());
            m.put("descrizione", c.descrizione());
            m.put("parametri", c.parametri());
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> m.get("nome").toString()));
        return out;
    }

    /** Gruppi noti: quelli assegnati agli utenti e quelli già usati nei processi. */
    @GetMapping("/gruppi")
    public List<Map<String, Object>> gruppi() {
        Map<String, Integer> conteggio = new TreeMap<>();
        users.findAll().forEach(u -> u.getGroups().forEach(g -> conteggio.merge(g, 1, Integer::sum)));
        for (ProcessDefinition d : repositoryService.createProcessDefinitionQuery().latestVersion().list()) {
            Process p = repositoryService.getBpmnModel(d.getId()).getMainProcess();
            p.getCandidateStarterGroups().forEach(g -> conteggio.putIfAbsent(g, 0));
            p.findFlowElementsOfType(UserTask.class, true)
                .forEach(t -> t.getCandidateGroups().forEach(g -> conteggio.putIfAbsent(g, 0)));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        conteggio.forEach((g, n) -> out.add(Map.of("nome", g, "utenti", n)));
        return out;
    }
}
