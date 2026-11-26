package com.eligesoft.orditogram.registro;

import com.eligesoft.orditogram.user.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * Registro degli errori imprevisti. Ogni errore riceve un codice breve (es. E-3F9A1C2B) che la persona vede nel
 * messaggio: con quel codice l'amministratore trova in Studio → Registro → Errori cosa è successo, senza che i
 * dettagli tecnici arrivino a chi usa l'applicazione.
 */
@Service
public class RegistroErrori {

    private static final Logger log = LoggerFactory.getLogger("orditogram.errori");
    private static final SecureRandom CASO = new SecureRandom();
    static final int MASSIMO_TRACCIA = 8000;

    private final RegistroErroreRepository repo;
    private final CurrentUserService currentUser;

    public RegistroErrori(RegistroErroreRepository repo, CurrentUserService currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    /** Registra l'errore e restituisce il suo codice. Non lancia mai: un registro che fallisce non deve coprire l'errore. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String registra(String origine, Throwable errore, String richiesta) {
        String codice = nuovoCodice();
        String utente = utente();
        log.error("Errore {} ({}) {} utente={}", codice, origine, richiesta, utente, errore);
        try {
            repo.save(new RegistroErrore(OffsetDateTime.now(), codice, origine, utente, RegistroService.taglia(richiesta, 500),
                RegistroService.taglia(errore.getClass().getName(), 255), messaggio(errore), traccia(errore),
                RegistroService.indirizzo()));
        } catch (RuntimeException e) {
            log.warn("Errore {} non salvato nel registro: {}", codice, e.getMessage());
        }
        return codice;
    }

    static String nuovoCodice() {
        byte[] b = new byte[4];
        CASO.nextBytes(b);
        return "E-" + HexFormat.of().withUpperCase().formatHex(b);
    }

    /** Il messaggio dell'errore e delle sue cause, su una riga ciascuno. */
    static String messaggio(Throwable e) {
        StringBuilder out = new StringBuilder();
        for (Throwable c = e; c != null && out.length() < 2000; c = c.getCause() == c ? null : c.getCause()) {
            if (!out.isEmpty()) out.append('\n');
            out.append(c.getClass().getSimpleName()).append(": ").append(String.valueOf(c.getMessage()).replaceAll("[\\r\\n]+", " "));
        }
        return RegistroService.taglia(out.toString(), 2000);
    }

    static String traccia(Throwable e) {
        StringWriter s = new StringWriter();
        e.printStackTrace(new PrintWriter(s));
        return RegistroService.taglia(s.toString(), MASSIMO_TRACCIA);
    }

    private String utente() {
        try {
            String u = currentUser.username();
            return u == null || "anonymousUser".equals(u) ? null : RegistroService.taglia(u, 100);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
