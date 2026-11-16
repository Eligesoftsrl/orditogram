package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regole contro l'aumento dei privilegi e il blocco dell'installazione, comuni a utenti e ruoli:
 *  - si danno, si tolgono o si modificano solo permessi che si hanno (un gestore utenti non crea amministratori,
 *    non cambia la password di un amministratore, non modifica un ruolo più potente del suo);
 *  - resta sempre almeno un amministratore attivo.
 */
@Service
public class ControlliAccesso {

    private final PermessiService permessi;
    private final CurrentUserService currentUser;
    private final AppUserRepository utenti;

    public ControlliAccesso(PermessiService permessi, CurrentUserService currentUser, AppUserRepository utenti) {
        this.permessi = permessi;
        this.currentUser = currentUser;
        this.utenti = utenti;
    }

    /** Chi agisce deve avere tutti i permessi indicati. */
    public void verificaEntroIMiei(Set<Permesso> coinvolti) {
        Set<Permesso> miei = permessi.permessiDi(currentUser.username());
        if (!miei.containsAll(coinvolti)) {
            String mancanti = coinvolti.stream().filter(p -> !miei.contains(p)).map(Permesso::nome)
                .sorted().collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Puoi dare, togliere o modificare solo permessi che hai anche tu (mancano: " + mancanti + ")");
        }
    }

    /** Chi agisce deve avere almeno tutti i permessi dell'utente indicato (com'è ora). */
    public void verificaPuoGestire(AppUser bersaglio) {
        verificaEntroIMiei(permessi.permessiDi(bersaglio));
    }

    /** Dopo una modifica (ancora da salvare): almeno un utente attivo deve restare amministratore. */
    public void verificaRestaUnAmministratore() {
        boolean resta = utenti.findAll().stream()
            .filter(AppUser::isEnabled)
            .anyMatch(u -> permessi.ruoliDi(u).stream().anyMatch(Ruolo::isAmministratore));
        if (!resta) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Deve restare almeno un amministratore attivo");
    }
}
