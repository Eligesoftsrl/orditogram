package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.accesso.Permesso;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Utility: chi è l'utente corrente e come si chiama per esteso. */
@Service
public class CurrentUserService {

    private final AppUserRepository repo;
    private final PermessiService permessi;

    public CurrentUserService(AppUserRepository repo, PermessiService permessi) {
        this.repo = repo;
        this.permessi = permessi;
    }

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    public AppUser current() {
        return repo.findByUsername(username()).orElseThrow();
    }

    /** Ha tutti i permessi (ruolo amministratore, diretto o da un gruppo). */
    public boolean isAdmin() {
        return permessi.amministratore(username());
    }

    /** L'utente corrente ha il permesso indicato. */
    public boolean puo(Permesso permesso) {
        return permessi.ha(username(), permesso);
    }

    public java.util.Set<String> groups() {
        return repo.findByUsername(username()).map(AppUser::gruppiEffettivi).orElse(java.util.Set.of());
    }

    /** L'utente corrente è esterno (cittadino, cliente, referente di un'organizzazione). */
    public boolean esterno() {
        return repo.findByUsername(username()).map(AppUser::isEsterno).orElse(false);
    }

    /** "mario" -> "Mario Rossi"; se l'utente non esiste restituisce lo username. */
    public String fullNameOf(String username) {
        if (username == null) return null;
        // sistemi esterni che usano l'API pubblica (api/IdentitaApi): "api:portale" -> "portale (sistema esterno)"
        if (username.startsWith("api:")) return username.substring(4) + " (sistema esterno)";
        return repo.findByUsername(username).map(AppUser::getFullName).orElse(username);
    }
}
