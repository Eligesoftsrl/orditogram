package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.accesso.Permesso;
import com.eligesoft.orditogram.accesso.ReferentiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
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
    private final ReferentiService referenti;
    private final AppUserRepository utenti;

    public MeController(CurrentUserService currentUser, PermessiService permessi, ReferentiService referenti,
                        AppUserRepository utenti) {
        this.currentUser = currentUser;
        this.permessi = permessi;
        this.referenti = referenti;
        this.utenti = utenti;
    }

    /** Il frontend lo chiede subito dopo l'accesso: qui si aggiorna l'ultimo accesso (al più una volta all'ora). */
    @GetMapping("/api/me")
    public Map<String, Object> me() {
        AppUser u = currentUser.current();
        OffsetDateTime adesso = OffsetDateTime.now();
        if (u.getUltimoAccesso() == null || u.getUltimoAccesso().isBefore(adesso.minusHours(1))) {
            u.setUltimoAccesso(adesso);
            u = utenti.save(u);
        }
        Set<Permesso> suoi = permessi.permessiDi(u);
        return Map.of(
            "username", u.getUsername(),
            "fullName", u.getFullName(),
            "groups", new TreeSet<>(u.getGroups()),
            "ruoli", permessi.ruoliDi(u).stream().map(r -> Map.of("codice", r.getCodice(), "nome", r.getNome())).toList(),
            "permessi", suoi.stream().map(Permesso::codice).collect(Collectors.toCollection(TreeSet::new)),
            "admin", suoi.containsAll(EnumSet.allOf(Permesso.class)),
            "esterno", u.isEsterno(),
            "passwordDaCambiare", u.isPasswordTemporanea(),
            "referenteDi", referenti.gestite(u).stream().map(o -> Map.of("id", o.getId(), "nome", o.getNome())).toList()
        );
    }
}
