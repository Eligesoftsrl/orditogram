package com.eligesoft.orditogram.auth;

import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.accesso.Ruolo;
import com.eligesoft.orditogram.accesso.RuoloRepository;
import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Dopo l'accesso sul provider: trova (o crea) l'utente in Orditogram, aggiorna nome, email e gruppi dal token
 * e prepara le autorizzazioni che usa Activiti (ROLE_ACTIVITI_USER, GROUP_x), esattamente come per gli utenti locali.
 * I permessi di Orditogram vengono dai ruoli (accesso/PermessiService): il gruppo amministratore del provider
 * (app.oidc.gruppo-admin) dà o toglie il ruolo amministratore; gli altri ruoli si legano ai gruppi nello Studio.
 */
@Service
public class OrditogramOidcUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(OrditogramOidcUserService.class);

    private final AppUserRepository utenti;
    private final OidcProperties proprieta;
    private final PasswordEncoder passwordEncoder;
    private final RuoloRepository ruoli;
    private final PermessiService permessi;

    public OrditogramOidcUserService(AppUserRepository utenti, OidcProperties proprieta, PasswordEncoder passwordEncoder,
                                     RuoloRepository ruoli, PermessiService permessi) {
        this.utenti = utenti;
        this.ruoli = ruoli;
        this.permessi = permessi;
        this.proprieta = proprieta;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest richiesta) throws OAuth2AuthenticationException {
        OidcUser utenteOidc = super.loadUser(richiesta);
        Map<String, Object> claims = utenteOidc.getClaims();

        String username = Objects.toString(claims.get(proprieta.getClaimUtente()), "").trim();
        if (username.isEmpty()) username = utenteOidc.getSubject();
        // "api:" è riservato ai sistemi esterni che usano l'API pubblica
        if (username.toLowerCase(java.util.Locale.ROOT).startsWith("api:")) username = "_" + username;
        if (username.length() > 100) username = username.substring(0, 100);
        final String nome = username;

        Set<String> gruppiToken = GruppiDalToken.leggi(claims, proprieta.getClaimGruppi(), proprieta.getMappaGruppi());
        boolean adminDaToken = proprieta.getGruppoAdmin() != null && gruppiToken.remove(proprieta.getGruppoAdmin());
        boolean esternoDaToken = proprieta.getGruppoEsterni() != null && gruppiToken.remove(proprieta.getGruppoEsterni());

        // l'identità stabile è emittente + soggetto, non il nome utente (che sul provider può cambiare)
        String soggetto = (utenteOidc.getIssuer() != null ? utenteOidc.getIssuer().toString() : "") + "|" + utenteOidc.getSubject();
        AppUser u = utenti.findByOidcSoggetto(soggetto).orElse(null);
        if (u == null) {
            AppUser omonimo = utenti.findByUsername(nome).orElse(null);
            if (omonimo != null) {
                if (omonimo.getOidcSoggetto() != null)
                    throw new OAuth2AuthenticationException(new OAuth2Error("utente_gia_collegato"),
                        "Il nome utente " + nome + " è già collegato a un'altra identità");
                // un utente locale con permessi di amministrazione non si collega da solo a un'identità esterna
                if (!permessi.permessiDi(omonimo).isEmpty() || !proprieta.isCollegaUtentiEsistenti())
                    throw new OAuth2AuthenticationException(new OAuth2Error("utente_locale"),
                        "Esiste già un utente locale " + nome + ": l'amministratore deve collegarlo");
                u = omonimo;
                log.info("Primo accesso OIDC: collegato l'utente esistente {}", nome);
            }
        }
        if (u == null) u = inAttesa(utenteOidc.getEmail(), utenteOidc.getEmailVerified()).orElse(null);
        if (u == null) {
            if (!proprieta.isCreaUtenti())
                throw new OAuth2AuthenticationException(new OAuth2Error("utente_non_abilitato"),
                    "L'utente " + nome + " non è abilitato a Orditogram");
            u = new AppUser();
            u.setUsername(nome);
            // nessuna password locale utilizzabile: entra solo dal provider
            u.setPassword(passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString()));
            log.info("Primo accesso OIDC: creato l'utente {}", nome);
        }
        u.setOidcSoggetto(soggetto);
        if (!u.isEnabled())
            throw new OAuth2AuthenticationException(new OAuth2Error("utente_disattivato"), "L'utente " + nome + " è disattivato in Orditogram");

        if (utenteOidc.getGivenName() != null) u.setFirstName(utenteOidc.getGivenName());
        if (utenteOidc.getFamilyName() != null) u.setLastName(utenteOidc.getFamilyName());
        if (u.getFirstName() == null && utenteOidc.getFullName() != null) u.setFirstName(utenteOidc.getFullName());
        if (utenteOidc.getEmail() != null) u.setEmail(utenteOidc.getEmail());
        if (proprieta.isSincronizzaGruppi() && claims.containsKey(radice(proprieta.getClaimGruppi())))
            u.setGroups(new HashSet<>(gruppiToken));
        if (proprieta.getGruppoAdmin() != null) {
            AppUser utente = u;
            ruoli.findByCodice(Ruolo.AMMINISTRATORE).ifPresent(admin -> {
                if (adminDaToken) utente.getRuoli().add(admin);
                else utente.getRuoli().removeIf(Ruolo::isAmministratore);
            });
        }
        if (proprieta.getGruppoEsterni() != null) {
            // utente esterno dal token: nessun ruolo, qualunque cosa dica il resto del token
            // chi è stato aggiunto dal referente di un'organizzazione resta esterno, qualunque cosa dica il token
            boolean esterno = esternoDaToken || u.getCreatoDa() != null;
            u.setTipo(esterno ? AppUser.ESTERNO : AppUser.INTERNO);
            if (esterno) u.getRuoli().clear();
        }
        u = utenti.save(u);

        List<GrantedAuthority> autorizzazioni = new ArrayList<>();
        autorizzazioni.add(new SimpleGrantedAuthority("ROLE_ACTIVITI_USER"));
        u.gruppiEffettivi().forEach(g -> autorizzazioni.add(new SimpleGrantedAuthority("GROUP_" + g)));

        // il "nome" dell'utente autenticato è lo username di Orditogram (anche se il claim non c'era)
        Map<String, Object> conNome = new LinkedHashMap<>(claims);
        conNome.put("iter_username", u.getUsername());
        return new DefaultOidcUser(autorizzazioni, utenteOidc.getIdToken(),
            new org.springframework.security.oauth2.core.oidc.OidcUserInfo(conNome), "iter_username");
    }

    /**
     * Persona aggiunta dal referente della sua organizzazione, che entra per la prima volta: si collega solo con
     * un'email che il provider dichiara verificata, e solo se l'indirizzo indica un'unica persona esterna in attesa.
     */
    Optional<AppUser> inAttesa(String email, Boolean verificata) {
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(verificata)) return Optional.empty();
        List<AppUser> trovati = utenti.findByEmailIgnoreCaseAndCreatoDaIsNotNullAndOidcSoggettoIsNull(email.trim());
        if (trovati.size() != 1 || !trovati.get(0).isEsterno()) return Optional.empty();
        log.info("Primo accesso OIDC: collegato l'utente {} aggiunto dal referente {}", trovati.get(0).getUsername(), trovati.get(0).getCreatoDa());
        return Optional.of(trovati.get(0));
    }

    private static String radice(String percorso) {
        int i = percorso.indexOf('.');
        return i < 0 ? percorso : percorso.substring(0, i);
    }
}
