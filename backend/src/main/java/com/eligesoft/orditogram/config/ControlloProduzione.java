package com.eligesoft.orditogram.config;

import com.eligesoft.orditogram.user.AppUserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Profilo {@code produzione} (SPRING_PROFILES_ACTIVE=produzione): Orditogram si rifiuta di partire con le
 * impostazioni pensate per lo sviluppo e le demo. Gira prima che si crei l'amministratore, quindi un'installazione
 * nuova non nasce mai con admin / admin.
 *
 * Blocca l'avvio: utenti o servizi di prova accesi, password del database o dell'amministratore predefinite o corte,
 * chiave di cifratura dei segreti assente o corta, utenti con le password note (admin, password) ancora attivi.
 * Avvisa soltanto: email non configurata, mittente di esempio.
 */
@Component
@Profile("produzione")
public class ControlloProduzione {

    private static final Logger log = LoggerFactory.getLogger(ControlloProduzione.class);

    static final int LUNGHEZZA_MINIMA_PASSWORD = 12;
    static final int LUNGHEZZA_MINIMA_CHIAVE = 32;

    /** Valori scritti negli esempi del progetto (application.yml, .env.example, docker-compose.yml, test). */
    static final Set<String> PASSWORD_NOTE = Set.of("admin", "password", "orditogram", "cambiami", "cambiami-subito");
    static final Set<String> CHIAVI_NOTE = Set.of("chiave-dei-test");

    /** Utenti che Orditogram crea da solo: l'amministratore e quelli di prova (UserDataInitializer). */
    static final List<String> UTENTI_PREDEFINITI =
        List.of("admin", "mario", "anna", "laura", "paolo", "chiara", "giulia", "marco", "elena");

    private final AppUserRepository utenti;
    private final PasswordEncoder encoder;
    private final boolean utentiProva;
    private final boolean serviziProva;
    private final boolean loginLocale;
    private final String passwordDatabase;
    private final String passwordAdmin;
    private final String chiaveCifratura;
    private final String hostEmail;
    private final String mittente;

    public ControlloProduzione(AppUserRepository utenti, PasswordEncoder encoder,
                               @Value("${app.demo.utenti:false}") boolean utentiProva,
                               @Value("${app.demo.servizi:true}") boolean serviziProva,
                               @Value("${app.login.locale:true}") boolean loginLocale,
                               @Value("${spring.datasource.password:}") String passwordDatabase,
                               @Value("${app.admin.password-iniziale:admin}") String passwordAdmin,
                               @Value("${app.sicurezza.chiave-cifratura:}") String chiaveCifratura,
                               @Value("${spring.mail.host:}") String hostEmail,
                               @Value("${app.notifiche.mittente:}") String mittente) {
        this.utenti = utenti;
        this.encoder = encoder;
        this.utentiProva = utentiProva;
        this.serviziProva = serviziProva;
        this.loginLocale = loginLocale;
        this.passwordDatabase = passwordDatabase;
        this.passwordAdmin = passwordAdmin;
        this.chiaveCifratura = chiaveCifratura;
        this.hostEmail = hostEmail;
        this.mittente = mittente;
    }

    @PostConstruct
    void verifica() {
        avvisi().forEach(a -> log.warn("Produzione: {}", a));
        List<String> problemi = problemi();
        if (problemi.isEmpty()) {
            log.info("Profilo di produzione: controlli superati");
            return;
        }
        StringBuilder testo = new StringBuilder("Orditogram non parte con il profilo di produzione finché non correggi:");
        for (int i = 0; i < problemi.size(); i++) testo.append(' ').append(i + 1).append(") ").append(problemi.get(i));
        testo.append(" Vedi il README, sezione \"Installazione in produzione\".");
        throw new IllegalStateException(testo.toString());
    }

    /** Ciò che impedisce l'avvio. */
    List<String> problemi() {
        List<String> problemi = new ArrayList<>();
        if (utentiProva)
            problemi.add("gli utenti di prova sono attivi (app.demo.utenti; nel .env UTENTI_DI_PROVA=false).");
        if (serviziProva)
            problemi.add("i servizi di prova sono attivi (app.demo.servizi; nel .env SERVIZI_DI_PROVA=false).");
        if (debole(passwordDatabase))
            problemi.add("la password del database è predefinita o più corta di " + LUNGHEZZA_MINIMA_PASSWORD
                + " caratteri (DB_PASSWORD).");
        if (chiaveCifratura == null || chiaveCifratura.isBlank() || chiaveCifratura.length() < LUNGHEZZA_MINIMA_CHIAVE
            || CHIAVI_NOTE.contains(chiaveCifratura))
            problemi.add("manca la chiave di cifratura dei segreti o è più corta di " + LUNGHEZZA_MINIMA_CHIAVE
                + " caratteri (ORDITOGRAM_CHIAVE_CIFRATURA, ad esempio con: openssl rand -base64 32).");
        if (loginLocale) {
            if (utenti.count() == 0) {
                if (debole(passwordAdmin))
                    problemi.add("la password iniziale dell'amministratore è predefinita o più corta di "
                        + LUNGHEZZA_MINIMA_PASSWORD + " caratteri (ADMIN_PASSWORD_INIZIALE).");
            } else {
                List<String> conPasswordNota = utentiConPasswordNota();
                if (!conPasswordNota.isEmpty())
                    problemi.add("questi utenti hanno ancora una password nota (admin o password): "
                        + String.join(", ", conPasswordNota) + ". Cambiala o disattiva l'utente.");
            }
        }
        return problemi;
    }

    /** Ciò che si segnala nel log senza fermare l'avvio. */
    List<String> avvisi() {
        List<String> avvisi = new ArrayList<>();
        if (hostEmail == null || hostEmail.isBlank())
            avvisi.add("email non configurata (SMTP_HOST): le notifiche restano solo nell'applicazione.");
        if (mittente == null || mittente.isBlank() || mittente.endsWith("@example.com"))
            avvisi.add("mittente delle email di esempio (EMAIL_MITTENTE).");
        return avvisi;
    }

    private static boolean debole(String password) {
        return password == null || password.length() < LUNGHEZZA_MINIMA_PASSWORD || PASSWORD_NOTE.contains(password);
    }

    /** Solo gli utenti creati da Orditogram: confrontare l'impronta di tutti gli utenti costerebbe troppo all'avvio. */
    private List<String> utentiConPasswordNota() {
        List<String> trovati = new ArrayList<>();
        for (String nome : UTENTI_PREDEFINITI)
            utenti.findByUsername(nome)
                .filter(u -> u.isEnabled() && u.getPassword() != null)
                .filter(u -> encoder.matches("admin", u.getPassword()) || encoder.matches("password", u.getPassword()))
                .ifPresent(u -> trovati.add(u.getUsername()));
        return trovati;
    }
}
