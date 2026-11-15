package com.eligesoft.orditogram.notifica;

import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Notifiche dell'utente corrente. */
@RestController
@RequestMapping("/api/notifiche")
public class NotificaController {

    private final NotificaRepository repo;
    private final CurrentUserService currentUser;

    public NotificaController(NotificaRepository repo, CurrentUserService currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> elenco() {
        String u = currentUser.username();
        return Map.of(
            "nonLette", repo.countByUsernameAndLettaFalse(u),
            "notifiche", repo.findByUsernameOrderByCreataIlDesc(u, PageRequest.of(0, 30)));
    }

    @PostMapping("/{id}/letta")
    @Transactional
    public void letta(@PathVariable Long id) {
        Notifica n = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!n.getUsername().equals(currentUser.username())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        n.setLetta(true);
    }

    @PostMapping("/lette")
    @Transactional
    public void tutteLette() {
        repo.segnaTutteLette(currentUser.username());
    }
}
