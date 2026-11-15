package com.eligesoft.orditogram.registro;

import com.eligesoft.orditogram.user.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;

/**
 * Registro delle azioni di amministrazione e sicurezza (pubblicazioni, form, utenti, pacchetti, accessi negati).
 * Le azioni sulle pratiche hanno già il loro storico (storico/StoricoService).
 */
@Service
public class RegistroService {

    private static final Logger log = LoggerFactory.getLogger("orditogram.registro");

    private final RegistroAzioneRepository repo;
    private final CurrentUserService currentUser;

    public RegistroService(RegistroAzioneRepository repo, CurrentUserService currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    /** Nella transazione in corso: se l'operazione fallisce, l'azione non risulta fatta. */
    @Transactional
    public void registra(String azione, String oggetto, String dettagli) {
        String utente = currentUser.username();
        if ("anonymousUser".equals(utente)) utente = null;
        salva(azione, oggetto, dettagli, utente, utente != null ? currentUser.fullNameOf(utente) : null);
    }

    /** In una transazione propria e con l'utente indicato: per gli eventi di sicurezza (es. accesso negato). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registraEvento(String azione, String utente, String dettagli) {
        salva(azione, utente, dettagli, utente, null);
    }

    private void salva(String azione, String oggetto, String dettagli, String utente, String nome) {
        RegistroAzione r = new RegistroAzione();
        r.setQuando(OffsetDateTime.now());
        r.setUtente(utente);
        r.setUtenteNome(nome);
        r.setAzione(azione);
        r.setOggetto(taglia(oggetto, 255));
        r.setDettagli(dettagli);
        r.setIndirizzo(indirizzo());
        repo.save(r);
        // anche nel log dell'applicazione, per chi lo raccoglie con strumenti esterni (SIEM)
        log.info("{} oggetto={} utente={} da={}", azione, oggetto, utente, r.getIndirizzo());
    }

    private static String indirizzo() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)) return null;
        HttpServletRequest req = a.getRequest();
        String inoltrato = req.getHeader("X-Forwarded-For");
        String ip = inoltrato != null && !inoltrato.isBlank() ? inoltrato.split(",")[0].trim() : req.getRemoteAddr();
        return taglia(ip, 64);
    }

    private static String taglia(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
