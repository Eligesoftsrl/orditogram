package com.eligesoft.orditogram.registro;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Accessi nel registro: i tentativi falliti e gli accessi con il provider (OIDC).
 * Gli accessi riusciti con password non si registrano: con Basic Auth ogni richiesta è un "accesso".
 */
@Component
public class EventiSicurezza {

    private final RegistroService registro;

    public EventiSicurezza(RegistroService registro) {
        this.registro = registro;
    }

    @EventListener
    public void fallito(AbstractAuthenticationFailureEvent e) {
        try {
            registro.registraEvento("ACCESSO_NEGATO", e.getAuthentication().getName(),
                e.getException().getClass().getSimpleName());
        } catch (Exception ignored) {
            // il registro non deve mai impedire di rispondere
        }
    }

    @EventListener
    public void accessoProvider(InteractiveAuthenticationSuccessEvent e) {
        try {
            registro.registraEvento("ACCESSO_PROVIDER", e.getAuthentication().getName(), null);
        } catch (Exception ignored) {
            // idem
        }
    }
}
