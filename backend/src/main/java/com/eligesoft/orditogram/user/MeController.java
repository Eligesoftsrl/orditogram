package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.accesso.Permesso;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Dati dell'utente loggato: usato dal frontend per il login e l'intestazione. */
@RestController
public class MeController {

    private final CurrentUserService currentUser;
    private final PermessiService permessi;

    public MeController(CurrentUserService currentUser, PermessiService permessi) {
        this.currentUser = currentUser;
        this.permessi = permessi;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me() {
        AppUser u = currentUser.current();
        Set<Permesso> suoi = permessi.permessiDi(u);
        return Map.of(
            "username", u.getUsername(),
            "fullName", u.getFullName(),
            "groups", new TreeSet<>(u.getGroups()),
            "ruoli", permessi.ruoliDi(u).stream().map(r -> Map.of("codice", r.getCodice(), "nome", r.getNome())).toList(),
            "permessi", suoi.stream().map(Permesso::codice).collect(Collectors.toCollection(TreeSet::new)),
            "admin", suoi.containsAll(EnumSet.allOf(Permesso.class)),
            "esterno", u.isEsterno()
        );
    }
}
