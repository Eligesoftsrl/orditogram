package com.eligesoft.orditogram.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * All'avvio:
 *  - se non esiste nessun utente crea l'amministratore (admin / admin)
 *  - con app.demo.utenti=true crea anche gli utenti di esempio mancanti (password: "password")
 */
@Component
public class UserDataInitializer implements CommandLineRunner {

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final boolean demo;
    private final String passwordAdmin;

    public UserDataInitializer(AppUserRepository repo, PasswordEncoder encoder,
                               @Value("${app.demo.utenti:false}") boolean demo,
                               @Value("${app.admin.password-iniziale:admin}") String passwordAdmin) {
        this.repo = repo;
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
        ensure("chiara", "Chiara", "Galli", Set.of("analisti"));
        ensure("giulia", "Giulia", "Russo", Set.of("revisori"));
    }

    private void ensure(String username, String first, String last, Set<String> groups) {
        if (!repo.existsByUsername(username)) create(username, "password", first, last, false, groups);
    }

    private void create(String username, String pwd, String first, String last,
                        boolean admin, Set<String> groups) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPassword(encoder.encode(pwd));
        u.setFirstName(first);
        u.setLastName(last);
        u.setAdmin(admin);
        u.setGroups(new HashSet<>(groups));
        repo.save(u);
    }
}
