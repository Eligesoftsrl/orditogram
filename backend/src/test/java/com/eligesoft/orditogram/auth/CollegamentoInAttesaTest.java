package com.eligesoft.orditogram.auth;

import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Primo accesso OIDC di una persona aggiunta dal referente: si collega solo con un'email verificata e univoca. */
class CollegamentoInAttesaTest {

    private final AppUserRepository utenti = mock(AppUserRepository.class);
    private final OrditogramOidcUserService servizio = new OrditogramOidcUserService(utenti, new OidcProperties(), null, null, null);

    private static AppUser persona(String tipo) {
        AppUser u = new AppUser();
        u.setUsername("anna@studio.it");
        u.setTipo(tipo);
        u.setCreatoDa("referente");
        return u;
    }

    @Test
    void collegaSoloConEmailVerificataEUnaSolaPersonaEsterna() {
        when(utenti.findByEmailIgnoreCaseAndCreatoDaIsNotNullAndOidcSoggettoIsNull("anna@studio.it"))
            .thenReturn(List.of(persona(AppUser.ESTERNO)));
        assertThat(servizio.inAttesa("anna@studio.it", true)).isPresent();
        assertThat(servizio.inAttesa("anna@studio.it", false)).isEmpty();
        assertThat(servizio.inAttesa("anna@studio.it", null)).isEmpty();
        assertThat(servizio.inAttesa(null, true)).isEmpty();

        when(utenti.findByEmailIgnoreCaseAndCreatoDaIsNotNullAndOidcSoggettoIsNull("doppia@studio.it"))
            .thenReturn(List.of(persona(AppUser.ESTERNO), persona(AppUser.ESTERNO)));
        assertThat(servizio.inAttesa("doppia@studio.it", true)).isEmpty();

        when(utenti.findByEmailIgnoreCaseAndCreatoDaIsNotNullAndOidcSoggettoIsNull("interno@ente.it"))
            .thenReturn(List.of(persona(AppUser.INTERNO)));
        assertThat(servizio.inAttesa("interno@ente.it", true)).isEmpty();
    }
}
