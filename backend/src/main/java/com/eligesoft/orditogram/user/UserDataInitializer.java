package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.accesso.Organizzazione;
import com.eligesoft.orditogram.accesso.OrganizzazioneRepository;
import com.eligesoft.orditogram.accesso.Ruolo;
import com.eligesoft.orditogram.accesso.RuoloRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * All'avvio:
 *  - se non esiste nessun utente crea l'amministratore (admin / admin)
 *  - con app.demo.utenti=true crea anche gli utenti di esempio mancanti (password: "password"), compresi due utenti
 *    esterni: marco (cittadino) ed elena (referente dello Studio legale Bianchi, organizzazione esterna)
 */
@Component
public class UserDataInitializer implements CommandLineRunner {

    private final AppUserRepository repo;
    private final RuoloRepository ruoli;
    private final OrganizzazioneRepository organizzazioni;
    private final com.eligesoft.orditogram.accesso.OrganizzazioneReferenteRepository referenti;
    private final PasswordEncoder encoder;
    private final boolean demo;
    private final Set<String> creati = new HashSet<>();
    private final String passwordAdmin;

    public UserDataInitializer(AppUserRepository repo, RuoloRepository ruoli, OrganizzazioneRepository organizzazioni,
                               com.eligesoft.orditogram.accesso.OrganizzazioneReferenteRepository referenti,
                               PasswordEncoder encoder,
                               @Value("${app.demo.utenti:false}") boolean demo,
                               @Value("${app.admin.password-iniziale:admin}") String passwordAdmin) {
        this.repo = repo;
        this.ruoli = ruoli;
        this.organizzazioni = organizzazioni;
        this.referenti = referenti;
        this.encoder = encoder;
        this.demo = demo;
        this.passwordAdmin = passwordAdmin;
    }

    @Override
    public void run(String... args) {
        if (repo.count() == 0) {
            create("admin", passwordAdmin, "Amministratore", "Sistema", true, Set.of());
            if ("admin".equals(passwordAdmin))
                org.slf4j.LoggerFactory.getLogger(UserDataInitializer.class).warn(
                    "Creato l'utente admin con la password predefinita \"admin\": cambiala subito "
                        + "(o imposta APP_ADMIN_PASSWORD_INIZIALE prima del primo avvio)");
        }
        if (!demo) return;
        ensure("mario", "Mario", "Rossi", Set.of("operatori"));
        ensure("anna", "Anna", "Bianchi", Set.of("operatori", "responsabili"));
        ensure("laura", "Laura", "Verdi", Set.of("dirigenti"));
        ensure("paolo", "Paolo", "Neri", Set.of("analisti"));
        // un analista che disegna processi e form senza essere amministratore
        if (creati.contains("paolo")) assegna("paolo", "analista");
        ensure("chiara", "Chiara", "Galli", Set.of("analisti"));
        ensure("giulia", "Giulia", "Russo", Set.of("revisori"));
        // utenti esterni: un cittadino e la referente di un'organizzazione esterna (vedi esempi/reclamo-controparte)
        ensure("marco", "Marco", "Ferri", Set.of());
        if (creati.contains("marco")) esterno("marco", null);
        ensure("elena", "Elena", "Conti", Set.of());
        if (creati.contains("elena")) esterno("elena", organizzazioni.findByCodice("studio-bianchi").orElseGet(() -> {
            Organizzazione o = new Organizzazione();
            o.setCodice("studio-bianchi");
            o.setNome("Studio legale Bianchi");
            o.setTipo(Organizzazione.ESTERNA);
            return organizzazioni.save(o);
        }));
        // elena è la referente dello studio: gestisce da sola i colleghi (portale → La mia organizzazione)
        repo.findByUsername("elena").filter(AppUser::isEsterno).ifPresent(elena ->
            organizzazioni.findByCodice("studio-bianchi")
                .filter(o -> elena.getOrganizzazioni().contains(o.getId()) && referenti.countByOrganizzazioneId(o.getId()) == 0)
                .ifPresent(o -> referenti.save(new com.eligesoft.orditogram.accesso.OrganizzazioneReferente(o.getId(), elena.getId(), null))));
    }

    private void esterno(String username, Organizzazione org) {
        repo.findByUsername(username).ifPresent(u -> {
            u.setTipo(AppUser.ESTERNO);
            if (org != null) u.getOrganizzazioni().add(org.getId());
            repo.save(u);
        });
    }

    private void ensure(String username, String first, String last, Set<String> groups) {
        if (!repo.existsByUsername(username)) {
            create(username, "password", first, last, false, groups);
            creati.add(username);
        }
    }

    private void assegna(String username, String ruolo) {
        repo.findByUsername(username).ifPresent(u -> ruoli.findByCodice(ruolo).ifPresent(r -> {
            u.getRuoli().add(r);
            repo.save(u);
        }));
    }

    private void create(String username, String pwd, String first, String last,
                        boolean admin, Set<String> groups) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPassword(encoder.encode(pwd));
        u.setFirstName(first);
        u.setLastName(last);
        // il ruolo amministratore viene creato dalla migrazione V3
        if (admin) ruoli.findByCodice(Ruolo.AMMINISTRATORE).ifPresent(r -> u.getRuoli().add(r));
        u.setGroups(new HashSet<>(groups));
        repo.save(u);
    }
}
