package com.eligesoft.orditogram.registro;

import com.eligesoft.orditogram.api.ErroriApi;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registra le letture dei metodi annotati con {@link RegistraLettura}: una riga se la lettura riesce, una riga
 * "negata" se viene rifiutata (permessi mancanti o oggetto non visibile, che per chi chiede è "non trovato").
 * Gli altri errori non sono letture: li registra il registro degli errori.
 */
@Aspect
@Component
public class AspettoLetture {

    private final RegistroLetture letture;

    public AspettoLetture(RegistroLetture letture) {
        this.letture = letture;
    }

    @Around("@annotation(lettura)")
    public Object registra(ProceedingJoinPoint jp, RegistraLettura lettura) throws Throwable {
        String oggetto = parametro(jp, lettura.oggetto());
        String dettagli = parametro(jp, lettura.dettagli());
        Object risultato;
        try {
            risultato = jp.proceed();
        } catch (Throwable t) {
            if (negata(t)) letture.registra(lettura.value(), oggetto, dettagli, RegistroLettura.NEGATA);
            throw t;
        }
        letture.registra(lettura.value(), oggetto, dettagli, RegistroLettura.CONSENTITA);
        return risultato;
    }

    static boolean negata(Throwable t) {
        if (t instanceof AccessDeniedException || t instanceof org.activiti.api.runtime.shared.NotFoundException) return true;
        if (t instanceof ResponseStatusException r)
            return r.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN) || r.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND);
        if (t instanceof ErroriApi.ErroreApi e) return e.stato() == 403 || e.stato() == 404;
        return false;
    }

    private static String parametro(ProceedingJoinPoint jp, String nome) {
        if (nome == null || nome.isBlank()) return null;
        String[] nomi = ((MethodSignature) jp.getSignature()).getParameterNames();
        Object[] valori = jp.getArgs();
        for (int i = 0; nomi != null && i < nomi.length; i++)
            if (nome.equals(nomi[i])) return valori[i] == null ? null : String.valueOf(valori[i]);
        throw new IllegalStateException("@RegistraLettura: parametro \"" + nome + "\" assente in " + jp.getSignature());
    }
}
