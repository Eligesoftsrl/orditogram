package com.eligesoft.orditogram.notifica;

import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Invia notifiche: sempre nell'applicazione (campanella), e anche via email
 * se è configurato un server SMTP (spring.mail.host) e l'utente ha un indirizzo.
 * L'email parte solo DOPO il commit: se il completamento del task fallisce, non si invia nulla.
 */
@Service
public class NotificaService {

    private static final Logger log = LoggerFactory.getLogger(NotificaService.class);

    private final NotificaRepository repo;
    private final AppUserRepository users;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mittente;

    public NotificaService(NotificaRepository repo, AppUserRepository users,
                           ObjectProvider<JavaMailSender> mailSender,
                           @Value("${app.notifiche.mittente:noreply@localhost}") String mittente) {
        this.repo = repo;
        this.users = users;
        this.mailSender = mailSender;
        this.mittente = mittente;
    }

    @Transactional
    public void invia(Collection<String> usernames, String titolo, String messaggio, String processInstanceId) {
        for (String u : new LinkedHashSet<>(usernames)) {
            Notifica n = new Notifica();
            n.setUsername(u);
            n.setTitolo(titolo);
            n.setMessaggio(messaggio);
            n.setProcessInstanceId(processInstanceId);
            n.setCreataIl(OffsetDateTime.now());
            repo.save(n);
            users.findByUsername(u).map(AppUser::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .ifPresent(email -> emailDopoCommit(email, titolo, messaggio));
        }
    }

    /**
     * Il server email è davvero configurato? (Con Docker spring.mail.host può esistere ma essere vuoto.)
     * Restituisce null se manca.
     */
    public static JavaMailSender configurato(JavaMailSender s) {
        if (s instanceof org.springframework.mail.javamail.JavaMailSenderImpl i) {
            String host = i.getHost();
            if (host == null || host.isBlank()) return null;
        }
        return s;
    }

    /** Destinatari: un utente, oppure "gruppo:<nome>" per tutti gli utenti attivi del gruppo. */
    public Set<String> risolvi(String destinatario) {
        Set<String> out = new LinkedHashSet<>();
        if (destinatario == null || destinatario.isBlank()) return out;
        for (String d : destinatario.split(",")) {
            d = d.trim();
            if (d.startsWith("gruppo:")) {
                String g = d.substring("gruppo:".length());
                users.findAll().stream()
                    .filter(u -> u.isEnabled() && u.getGroups().contains(g))
                    .forEach(u -> out.add(u.getUsername()));
            } else if (!d.isEmpty()) {
                out.add(d);
            }
        }
        return out;
    }

    private void emailDopoCommit(String to, String subject, String text) {
        JavaMailSender sender = configurato(mailSender.getIfAvailable());
        if (sender == null) return;   // email non configurata: solo notifica in app
        Runnable send = () -> {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(mittente);
                m.setTo(to);
                m.setSubject(subject);
                m.setText(text);
                sender.send(m);
            } catch (Exception e) {
                log.warn("Invio email a {} fallito: {}", to, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
