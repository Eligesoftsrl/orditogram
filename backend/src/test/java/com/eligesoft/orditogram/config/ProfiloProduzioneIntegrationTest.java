package com.eligesoft.orditogram.config;

import com.eligesoft.orditogram.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un'installazione nuova con il profilo di produzione e impostazioni sicure: parte, crea solo l'amministratore
 * con la password scelta e nessun utente di prova, e il cookie di sessione viaggia solo su HTTPS.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:produzione;DB_CLOSE_DELAY=-1",
    "spring.datasource.password=una-password-del-database",
    "app.demo.utenti=false",
    "app.demo.servizi=false",
    "app.admin.password-iniziale=una-password-lunga-e-nuova",
    "app.sicurezza.chiave-cifratura=k3Jx9vQ2mP7rT5wZ8bN4cF6hL1sD0aGy"
})
@ActiveProfiles({"test", "produzione"})
class ProfiloProduzioneIntegrationTest {

    @Autowired AppUserRepository utenti;
    @Autowired ControlloProduzione controllo;
    @Value("${server.servlet.session.cookie.secure}") boolean cookieSicuro;

    @Test
    void partonoSoloLAmministratoreEIlCookieSicuro() {
        assertThat(utenti.findByUsername("admin")).isPresent();
        assertThat(utenti.findByUsername("mario")).isEmpty();
        assertThat(cookieSicuro).isTrue();
        assertThat(controllo.problemi()).isEmpty();
    }
}
