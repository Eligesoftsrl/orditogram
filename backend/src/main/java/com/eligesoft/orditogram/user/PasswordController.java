package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.registro.RegistroService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Cambio della propria password (solo login locale). Obbligatorio al primo accesso quando la password è stata data
 * da altri (es. dal referente dell'organizzazione): finché non la cambia, l'utente può fare solo questo
 * (vedi accesso/AccessoEsterni).
 */
@RestController
public class PasswordController {

    /** Lunghezza minima: le linee guida AgID e NIST preferiscono la lunghezza alla complessità. */
    static final int MINIMO = 12;

    public record CambioPassword(String attuale, String nuova) { }

    private final CurrentUserService currentUser;
    private final AppUserRepository utenti;
    private final PasswordEncoder encoder;
    private final RegistroService registro;
    private final boolean loginLocale;

    public PasswordController(CurrentUserService currentUser, AppUserRepository utenti, PasswordEncoder encoder,
                              RegistroService registro, @Value("${app.login.locale:true}") boolean loginLocale) {
        this.currentUser = currentUser;
        this.utenti = utenti;
        this.encoder = encoder;
        this.registro = registro;
        this.loginLocale = loginLocale;
    }

    @PostMapping("/api/me/password")
    @Transactional
    public void cambia(@RequestBody CambioPassword cambio) {
        if (!loginLocale) throw bad("La password si cambia sul sistema di accesso centrale (il provider con cui entri)");
        AppUser u = currentUser.current();
        if (u.getOidcSoggetto() != null && !u.isPasswordTemporanea()) throw bad("Entri con il provider: la password si cambia lì");
        String attuale = cambio.attuale() == null ? "" : cambio.attuale();
        String nuova = cambio.nuova() == null ? "" : cambio.nuova();
        if (!encoder.matches(attuale, u.getPassword())) {
            registro.registraEvento("ACCESSO_NEGATO", u.getUsername(), "Cambio password: password attuale errata");
            throw bad("La password attuale non è corretta");
        }
        if (nuova.length() < MINIMO) throw bad("La nuova password deve avere almeno " + MINIMO + " caratteri");
        if (nuova.length() > 128) throw bad("La nuova password è troppo lunga (al massimo 128 caratteri)");
        if (nuova.equals(attuale)) throw bad("La nuova password deve essere diversa da quella attuale");
        if (nuova.toLowerCase(Locale.ROOT).contains(u.getUsername().toLowerCase(Locale.ROOT)))
            throw bad("La nuova password non può contenere il nome utente");
        u.setPassword(encoder.encode(nuova));
        u.setPasswordTemporanea(false);
        utenti.save(u);
        registro.registra("PASSWORD_CAMBIATA", u.getUsername(), "Cambiata dall'utente");
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
