package com.eligesoft.orditogram.messaggio;

import com.eligesoft.orditogram.api.Ambiti;
import com.eligesoft.orditogram.api.ChiaviApiService;
import com.eligesoft.orditogram.api.IdentitaApi;
import com.eligesoft.orditogram.storico.PraticaAccessService;
import com.eligesoft.orditogram.storico.StoricoService;
import org.activiti.engine.TaskService;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Messaggi in arrivo dall'esterno (sistemi autorizzati con chiave) o registrati a mano da un operatore.
 *
 *   POST /api/messaggi/integrazioniRicevute
 *   X-API-Key: <chiave del sistema>
 *   { "cerca": { "numeroProtocollo": "2026/000123" }, "dati": { "noteIntegrazioni": "Inviata la planimetria" } }
 *
 * oppure con l'id della pratica: { "pratica": "12345", "dati": { ... } }
 */
@RestController
public class MessaggiController {

    private final MessaggiService messaggi;
    private final ChiaviApiService chiavi;
    private final PraticaAccessService access;
    private final CurrentUserService currentUser;
    private final TaskService taskService;
    private final StoricoService storico;

    public MessaggiController(MessaggiService messaggi, ChiaviApiService chiavi, PraticaAccessService access,
                              CurrentUserService currentUser, TaskService taskService, StoricoService storico) {
        this.messaggi = messaggi;
        this.chiavi = chiavi;
        this.access = access;
        this.currentUser = currentUser;
        this.taskService = taskService;
        this.storico = storico;
    }

    public record Richiesta(String pratica, Map<String, Object> cerca, Map<String, Object> dati) {}

    @PostMapping("/api/messaggi/{nome}")
    public Map<String, Object> daSistema(@PathVariable String nome, @RequestBody(required = false) Richiesta r,
                                         @RequestHeader(value = "X-API-Key", required = false) String chiave) {
        // chiavi in configurazione (app.messaggi.chiavi) o create nello Studio con il permesso "messaggi:invia"
        IdentitaApi sistema = chiavi.verifica(chiave).filter(i -> i.puo(Ambiti.MESSAGGI_INVIA))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chiave API mancante o non valida"));
        Richiesta req = r != null ? r : new Richiesta(null, null, null);
        List<String> pratiche = messaggi.consegna(nome, req.pratica(), req.cerca(), req.dati(),
            "Sistema " + sistema.sistema(), sistema.processi());
        return Map.of("consegnato", true, "pratiche", pratiche);
    }

    /** Un operatore registra l'arrivo (es. ha ricevuto la PEC): stessa strada nel processo. */
    @PostMapping("/api/pratiche/{pi}/messaggi/{nome}")
    public Map<String, Object> aMano(@PathVariable String pi, @PathVariable String nome,
                                     @RequestBody(required = false) Map<String, Object> dati) {
        access.checkView(pi);
        // può registrarlo: l'amministratore, chi ha avviato la pratica, chi può lavorare un'attività della pratica
        String utente = currentUser.username();
        var gruppi = new java.util.ArrayList<>(currentUser.groups());
        boolean puo = currentUser.isAdmin()
            || storico.iniziatore(pi).map(utente::equals).orElse(false)
            || (gruppi.isEmpty()
                ? taskService.createTaskQuery().processInstanceId(pi).taskCandidateOrAssigned(utente).count() > 0
                : taskService.createTaskQuery().processInstanceId(pi).taskCandidateOrAssigned(utente, gruppi).count() > 0);
        if (!puo) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Possono registrare l'arrivo l'amministratore, chi ha avviato la pratica o chi ne lavora un'attività");
        boolean atteso = messaggi.attesi(pi).stream().anyMatch(a -> a.nome().equals(nome));
        if (!atteso) throw new ResponseStatusException(HttpStatus.CONFLICT, "La pratica non sta aspettando questo messaggio");
        String chi = currentUser.fullNameOf(currentUser.username());
        messaggi.consegna(nome, pi, null, dati, chi + " (registrato a mano)");
        return Map.of("consegnato", true);
    }
}
