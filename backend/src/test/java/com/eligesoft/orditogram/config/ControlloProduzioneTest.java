package com.eligesoft.orditogram.config;

import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Il profilo di produzione rifiuta le impostazioni di sviluppo e demo, e lascia partire quelle sicure. */
class ControlloProduzioneTest {

    private static final String PASSWORD_BUONA = "una-password-lunga-e-nuova";
    private static final String CHIAVE_BUONA = "k3Jx9vQ2mP7rT5wZ8bN4cF6hL1sD0aGy";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AppUserRepository utenti = mock(AppUserRepository.class);

    private ControlloProduzione controllo(boolean utentiProva, boolean serviziProva, String passwordDb,
                                          String passwordAdmin, String chiave) {
        return new ControlloProduzione(utenti, encoder, utentiProva, serviziProva, false, true, passwordDb, passwordAdmin,
            chiave, "smtp.azienda.it", "orditogram@azienda.it");
    }

    private ControlloProduzione sicuro() {
        return controllo(false, false, PASSWORD_BUONA, PASSWORD_BUONA, CHIAVE_BUONA);
    }

    private AppUser utente(String nome, String password, boolean attivo) {
        AppUser u = new AppUser();
        u.setUsername(nome);
        u.setPassword(encoder.encode(password));
        u.setEnabled(attivo);
        return u;
    }

    @Test
    void impostazioniSicurePartono() {
        when(utenti.count()).thenReturn(0L);
        assertThat(sicuro().problemi()).isEmpty();
        sicuro().verifica();
    }

    @Test
    void impostazioniDiSviluppoNonPartono() {
        when(utenti.count()).thenReturn(0L);
        ControlloProduzione c = controllo(true, true, "orditogram", "admin", "");

        assertThat(c.problemi()).hasSize(5)
            .anySatisfy(p -> assertThat(p).contains("utenti di prova"))
            .anySatisfy(p -> assertThat(p).contains("servizi di prova"))
            .anySatisfy(p -> assertThat(p).contains("DB_PASSWORD"))
            .anySatisfy(p -> assertThat(p).contains("ORDITOGRAM_CHIAVE_CIFRATURA"))
            .anySatisfy(p -> assertThat(p).contains("ADMIN_PASSWORD_INIZIALE"));
        assertThatThrownBy(c::verifica).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("1) ").hasMessageContaining("5) ").hasMessageContaining("Installazione in produzione");
    }

    @Test
    void passwordDegliEsempiECorteSonoRifiutate() {
        when(utenti.count()).thenReturn(0L);
        assertThat(controllo(false, false, "cambiami", "cambiami-subito", CHIAVE_BUONA).problemi()).hasSize(2);
        assertThat(controllo(false, false, "corta", "ancoracorta", CHIAVE_BUONA).problemi()).hasSize(2);
        assertThat(controllo(false, false, PASSWORD_BUONA, PASSWORD_BUONA, "chiave-dei-test").problemi())
            .singleElement().asString().contains("chiave di cifratura");
    }

    @Test
    void utentiConPasswordNotaBloccanoLAvvio() {
        when(utenti.count()).thenReturn(3L);
        when(utenti.findByUsername(anyString())).thenReturn(Optional.empty());
        when(utenti.findByUsername("admin")).thenReturn(Optional.of(utente("admin", "admin", true)));
        when(utenti.findByUsername("mario")).thenReturn(Optional.of(utente("mario", "password", true)));
        // disattivato: non entra, quindi non è un problema
        when(utenti.findByUsername("anna")).thenReturn(Optional.of(utente("anna", "password", false)));

        assertThat(sicuro().problemi()).singleElement().asString()
            .contains("admin, mario").doesNotContain("anna");
    }

    @Test
    void conPasswordCambiateParte() {
        when(utenti.count()).thenReturn(1L);
        when(utenti.findByUsername(anyString())).thenReturn(Optional.empty());
        when(utenti.findByUsername("admin")).thenReturn(Optional.of(utente("admin", PASSWORD_BUONA, true)));
        assertThat(sicuro().problemi()).isEmpty();
    }

    @Test
    void processiDiEsempioNonPartono() {
        when(utenti.count()).thenReturn(0L);
        ControlloProduzione conEsempi = new ControlloProduzione(utenti, encoder, false, false, true, true, PASSWORD_BUONA,
            PASSWORD_BUONA, CHIAVE_BUONA, "smtp.azienda.it", "orditogram@azienda.it");
        assertThat(conEsempi.problemi()).singleElement().satisfies(p -> assertThat(p).contains("PROCESSI_DI_PROVA"));
    }

    @Test
    void senzaAccessoLocaleLePasswordDegliUtentiNonContano() {
        ControlloProduzione soloOidc = new ControlloProduzione(utenti, encoder, false, false, false, false, PASSWORD_BUONA,
            "admin", CHIAVE_BUONA, "smtp.azienda.it", "orditogram@azienda.it");
        assertThat(soloOidc.problemi()).isEmpty();
    }

    @Test
    void emailMancanteEUnAvvisoNonUnBlocco() {
        when(utenti.count()).thenReturn(0L);
        ControlloProduzione senzaEmail = new ControlloProduzione(utenti, encoder, false, false, false, true, PASSWORD_BUONA,
            PASSWORD_BUONA, CHIAVE_BUONA, "", "noreply@example.com");
        assertThat(senzaEmail.problemi()).isEmpty();
        assertThat(senzaEmail.avvisi()).hasSize(2);
    }
}
