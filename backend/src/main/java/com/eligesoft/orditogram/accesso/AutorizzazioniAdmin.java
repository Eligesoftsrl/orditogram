package com.eligesoft.orditogram.accesso;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** Regole di SecurityConfig per le sezioni di amministrazione (/api/admin/**), lette dai permessi dell'utente. */
@Component
public class AutorizzazioniAdmin {

    private final PermessiService permessi;

    public AutorizzazioniAdmin(PermessiService permessi) {
        this.permessi = permessi;
    }

    /** Basta uno dei permessi indicati. */
    public AuthorizationManager<RequestAuthorizationContext> uno(Permesso... richiesti) {
        return (autenticazione, contesto) -> {
            Set<Permesso> miei = suoi(autenticazione.get());
            return new AuthorizationDecision(Arrays.stream(richiesti).anyMatch(miei::contains));
        };
    }

    /** Servono tutti i permessi indicati; senza argomenti, tutti quelli esistenti (solo l'amministratore). */
    public AuthorizationManager<RequestAuthorizationContext> tutti(Permesso... richiesti) {
        Set<Permesso> servono = richiesti.length == 0 ? EnumSet.allOf(Permesso.class) : EnumSet.copyOf(Arrays.asList(richiesti));
        return (autenticazione, contesto) -> new AuthorizationDecision(suoi(autenticazione.get()).containsAll(servono));
    }

    private Set<Permesso> suoi(Authentication a) {
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return EnumSet.noneOf(Permesso.class);
        return permessi.permessiDi(a.getName());
    }
}
