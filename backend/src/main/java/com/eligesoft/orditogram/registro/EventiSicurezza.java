package com.eligesoft.orditogram.registro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accessi nel registro: i tentativi falliti, gli accessi con il provider (OIDC), gli accessi con utente e password
 * e le uscite.
 *
 * Con l'accesso locale il browser manda utente e password a ogni richiesta (Basic): un "accesso" si registra solo
 * se la stessa persona non ne ha uno registrato negli ultimi {@code app.registro.accesso-ogni-minuti} minuti.
 */
@Component
public class EventiSicurezza {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventiSicurezza.class);

    private final RegistroService registro;
    private final Duration finestra;
    private final Map<String, Instant> ultimiAccessi = new ConcurrentHashMap<>();

    public EventiSicurezza(RegistroService registro, @Value("${app.registro.accesso-ogni-minuti:30}") int minuti) {
        this.registro = registro;
        this.finestra = Duration.ofMinutes(Math.max(1, minuti));
    }

    @EventListener
    public void fallito(AbstractAuthenticationFailureEvent e) {
        try {
            registro.registraEvento("ACCESSO_NEGATO", e.getAuthentication().getName(),
                e.getException().getClass().getSimpleName());
        } catch (Exception ex) {
            // il registro non deve mai impedire di rispondere
            log.warn("Accesso negato non registrato: {}", ex.getMessage());
        }
    }

    @EventListener
    public void accessoProvider(InteractiveAuthenticationSuccessEvent e) {
        try {
            registro.registraEvento("ACCESSO_PROVIDER", e.getAuthentication().getName(), null);
        } catch (Exception ex) {
            log.warn("Accesso con il provider non registrato: {}", ex.getMessage());
        }
    }

    @EventListener
    public void accessoLocale(AuthenticationSuccessEvent e) {
        if (!(e.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) return;
        String utente = e.getAuthentication().getName();
        Instant adesso = Instant.now();
        Instant precedente = ultimiAccessi.get(utente);
        if (precedente != null && precedente.plus(finestra).isAfter(adesso)) return;
        ultimiAccessi.put(utente, adesso);
        if (ultimiAccessi.size() > 10_000) ultimiAccessi.entrySet().removeIf(v -> v.getValue().plus(finestra).isBefore(adesso));
        try {
            registro.registraEvento("ACCESSO", utente, "utente e password");
        } catch (Exception ex) {
            log.warn("Accesso non registrato: {}", ex.getMessage());
        }
    }

    @EventListener
    public void uscita(LogoutSuccessEvent e) {
        if (e.getAuthentication() == null) return;
        String utente = e.getAuthentication().getName();
        ultimiAccessi.remove(utente);
        try {
            registro.registraEvento("USCITA", utente, null);
        } catch (Exception ex) {
            log.warn("Uscita non registrata: {}", ex.getMessage());
        }
    }
}
