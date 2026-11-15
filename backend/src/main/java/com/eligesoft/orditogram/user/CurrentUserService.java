package com.eligesoft.orditogram.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Utility: chi è l'utente corrente e come si chiama per esteso. */
@Service
public class CurrentUserService {

    private final AppUserRepository repo;

    public CurrentUserService(AppUserRepository repo) {
        this.repo = repo;
    }

    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    public AppUser current() {
        return repo.findByUsername(username()).orElseThrow();
    }

    public boolean isAdmin() {
        return repo.findByUsername(username()).map(AppUser::isAdmin).orElse(false);
    }

    public java.util.Set<String> groups() {
        return repo.findByUsername(username()).map(AppUser::getGroups).orElse(java.util.Set.of());
    }

    /** "mario" -> "Mario Rossi"; se l'utente non esiste restituisce lo username. */
    public String fullNameOf(String username) {
        if (username == null) return null;
        // sistemi esterni che usano l'API pubblica (api/IdentitaApi): "api:portale" -> "portale (sistema esterno)"
        if (username.startsWith("api:")) return username.substring(4) + " (sistema esterno)";
        return repo.findByUsername(username).map(AppUser::getFullName).orElse(username);
    }
}
