package com.eligesoft.orditogram.registro;

import com.eligesoft.orditogram.user.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** Scrive il registro delle letture (vedi {@link RegistraLettura}). Non blocca mai la lettura: se fallisce, lo dice nel log. */
@Service
public class RegistroLetture {

    private static final Logger log = LoggerFactory.getLogger(RegistroLetture.class);
    private static final Logger siem = LoggerFactory.getLogger("orditogram.letture");

    private final RegistroLetturaRepository repo;
    private final CurrentUserService currentUser;

    public RegistroLetture(RegistroLetturaRepository repo, CurrentUserService currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    /** In una transazione propria: resta anche quando la richiesta fallisce (le letture negate contano di più). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registra(String azione, String oggetto, String dettagli, String esito) {
        try {
            String utente = utente();
            String indirizzo = RegistroService.indirizzo();
            repo.save(new RegistroLettura(OffsetDateTime.now(), utente, azione, RegistroService.taglia(oggetto, 255),
                RegistroService.taglia(dettagli, 500), esito, indirizzo));
            siem.info("{} oggetto={} esito={} utente={} da={}", azione, oggetto, esito, utente, indirizzo);
        } catch (RuntimeException e) {
            log.warn("Lettura {} di {} non registrata: {}", azione, oggetto, e.getMessage());
        }
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
