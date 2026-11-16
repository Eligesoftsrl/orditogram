package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Negazione per default per gli utenti esterni (cittadini, clienti, referenti di organizzazioni): possono usare solo
 * le API del portale elencate qui. Tutto il resto (amministrazione, diagrammi, anagrafiche, messaggi, pratiche
 * collegate) risponde 403, anche se un domani si aggiunge un'API e ci si dimentica di proteggerla.
 * Il tipo dell'utente si legge dal database a ogni richiesta, come i permessi.
 * Chi ha una password temporanea (interno o esterno) può solo cambiarla: lo si controlla qui per le richieste
 * del portale; le sezioni di amministrazione chiedono permessi che un utente così non ha (vedi ReferentiService).
 */
@Component
public class AccessoEsterni {

    private static final PathPatternRequestMatcher.Builder P = PathPatternRequestMatcher.withDefaults();

    /** Le sole richieste consentite a un utente esterno; il controllo sulla singola pratica resta nei controller. */
    static final List<RequestMatcher> CONSENTITE = List.of(
        P.matcher(HttpMethod.GET, "/api/me"),
        P.matcher(HttpMethod.GET, "/api/info"),
        P.matcher("/api/auth/**"),
        P.matcher(HttpMethod.GET, "/api/calendario/**"),
        P.matcher("/api/notifiche/**"),
        // processi che può avviare e avvio
        P.matcher(HttpMethod.GET, "/api/processes"),
        P.matcher(HttpMethod.POST, "/api/processes/*/start"),
        // le sue attività (non il diagramma del processo)
        P.matcher(HttpMethod.GET, "/api/tasks"),
        P.matcher(HttpMethod.GET, "/api/tasks/*"),
        P.matcher(HttpMethod.POST, "/api/tasks/*/claim"),
        P.matcher(HttpMethod.POST, "/api/tasks/*/release"),
        P.matcher(HttpMethod.POST, "/api/tasks/*/complete"),
        // le sue pratiche e i documenti visibili alle parti (non il diagramma né le tipologie)
        P.matcher(HttpMethod.GET, "/api/pratiche"),
        P.matcher(HttpMethod.GET, "/api/pratiche/*"),
        P.matcher(HttpMethod.GET, "/api/pratiche/*/documenti"),
        P.matcher(HttpMethod.POST, "/api/pratiche/*/documenti"),
        P.matcher(HttpMethod.POST, "/api/documenti/*/versioni"),
        P.matcher(HttpMethod.GET, "/api/documenti/versioni/*/file"),
        P.matcher(HttpMethod.GET, "/api/documenti/versioni/*/contenuto"),
        // cambio della propria password e, per i referenti, la propria organizzazione (controlli nel servizio)
        P.matcher(HttpMethod.POST, "/api/me/password"),
        P.matcher("/api/organizzazione/**"));

    /** Con una password temporanea (data da altri) si può solo vedere chi si è e cambiarla. */
    static final List<RequestMatcher> CON_PASSWORD_TEMPORANEA = List.of(
        P.matcher(HttpMethod.GET, "/api/me"),
        P.matcher(HttpMethod.GET, "/api/info"),
        P.matcher("/api/auth/**"),
        P.matcher(HttpMethod.POST, "/api/me/password"));

    private final AppUserRepository utenti;

    public AccessoEsterni(AppUserRepository utenti) {
        this.utenti = utenti;
    }

    /** Per le richieste non coperte da altre regole: autenticati, e per gli esterni solo le richieste del portale. */
    public AuthorizationManager<RequestAuthorizationContext> autenticati() {
        return (autenticazione, contesto) -> {
            Authentication a = autenticazione.get();
            if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken)
                return new AuthorizationDecision(false);
            AppUser u = utenti.findByUsername(a.getName()).orElse(null);
            if (u == null) return new AuthorizationDecision(true);  // es. sistemi senza utente nel database
            HttpServletRequest req = contesto.getRequest();
            if (u.isPasswordTemporanea()) return new AuthorizationDecision(CON_PASSWORD_TEMPORANEA.stream().anyMatch(m -> m.matches(req)));
            return new AuthorizationDecision(!u.isEsterno() || consentita(req));
        };
    }

    static boolean consentita(HttpServletRequest req) {
        return CONSENTITE.stream().anyMatch(m -> m.matches(req));
    }
}
