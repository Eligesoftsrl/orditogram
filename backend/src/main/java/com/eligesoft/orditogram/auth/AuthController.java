package com.eligesoft.orditogram.auth;

import com.eligesoft.orditogram.user.UtentiProvaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** Come si può accedere: login locale e/o provider OIDC configurati. Pubblico, serve alla pagina di accesso. */
@RestController
public class AuthController {

    private final ObjectProvider<ClientRegistrationRepository> registrazioni;
    private final boolean locale;
    private final UtentiProvaService utentiProva;

    public AuthController(ObjectProvider<ClientRegistrationRepository> registrazioni,
                          @Value("${app.login.locale:true}") boolean locale,
                          UtentiProvaService utentiProva) {
        this.registrazioni = registrazioni;
        this.locale = locale;
        this.utentiProva = utentiProva;
    }

    /**
     * Metodi di accesso e, solo su localhost con gli utenti di prova accesi e fuori dalla produzione, gli accessi
     * rapidi agli attori della demo (vedi UtentiProvaService).
     */
    @GetMapping("/api/auth/metodi")
    public Map<String, Object> metodi(HttpServletRequest richiesta) {
        List<Map<String, String>> oidc = new ArrayList<>();
        ClientRegistrationRepository repo = registrazioni.getIfAvailable();
        if (repo instanceof Iterable<?> elenco) {
            for (Object o : elenco) {
                if (o instanceof ClientRegistration r)
                    oidc.add(Map.of("id", r.getRegistrationId(), "nome", r.getClientName(),
                        "url", "/oauth2/authorization/" + r.getRegistrationId()));
            }
        }
        boolean rapidi = utentiProva.accessiRapidi(richiesta.getServerName());
        return Map.of("locale", locale, "oidc", oidc, "utentiProva", rapidi,
            "accessiRapidi", rapidi ? utentiProva.elenco() : List.of());
    }
}
