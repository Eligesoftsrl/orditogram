package com.eligesoft.orditogram;

import com.eligesoft.orditogram.accesso.AutorizzazioniAdmin;
import com.eligesoft.orditogram.accesso.Permesso;
import com.eligesoft.orditogram.api.ApiProperties;
import com.eligesoft.orditogram.api.ChiaviApiService;
import com.eligesoft.orditogram.api.FiltroChiaveApi;
import com.eligesoft.orditogram.api.LimiteRichieste;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import com.eligesoft.orditogram.auth.OrditogramOidcUserService;
import com.eligesoft.orditogram.auth.OidcProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

/**
 * Accesso:
 *  - utenti locali su Postgres (user/DbUserDetailsService), con Basic Auth dal frontend o dalle integrazioni;
 *  - se configurato, un identity provider OIDC (Keycloak, Microsoft Entra ID): accesso con redirect e sessione,
 *    utenti e gruppi sincronizzati da auth/OrditogramOidcUserService.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * API pubblica (/api/v1): solo chiavi API, nessuna sessione, nessun cookie, errori in JSON.
     * Viene prima della catena principale, che non vede mai queste richieste.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiPubblica(HttpSecurity http, ChiaviApiService chiavi, ApiProperties api) throws Exception {
        http.securityMatcher("/api/v1/**")
            .csrf(csrf -> csrf.disable())  // NOSONAR: solo chiave API nell'intestazione, nessun cookie né sessione
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .requestCache(c -> c.disable())
            .addFilterBefore(new FiltroChiaveApi(chiavi, new LimiteRichieste(api.getRichiesteAlMinuto())),
                org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/api/v1/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    scriviErrore(res, 401, "chiave_mancante", "Serve una chiave API (X-API-Key)"))
                .accessDeniedHandler((req, res, ex) ->
                    scriviErrore(res, 403, "permesso_mancante", "Operazione non consentita a questa chiave")));
        List<String> origini = api.getCorsOrigini().stream().filter(o -> o != null && !o.isBlank()).toList();
        if (origini.isEmpty()) {
            http.cors(c -> c.disable());
        } else {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(origini);
            cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            cors.setAllowedHeaders(List.of("X-API-Key", "Authorization", "Content-Type"));
            cors.setExposedHeaders(List.of("Location", "Retry-After"));
            cors.setMaxAge(3600L);
            UrlBasedCorsConfigurationSource sorgente = new UrlBasedCorsConfigurationSource();
            sorgente.registerCorsConfiguration("/api/v1/**", cors);
            http.cors(c -> c.configurationSource(sorgente));
        }
        return http.build();
    }

    private static void scriviErrore(HttpServletResponse res, int stato, String codice, String messaggio) throws IOException {
        res.setStatus(stato);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"errore\":{\"codice\":\"" + codice + "\",\"messaggio\":\"" + messaggio + "\"}}");
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ObjectProvider<ClientRegistrationRepository> registrazioni,
                                           ObjectProvider<OrditogramOidcUserService> oidcUserService,
                                           OidcProperties oidc,
                                           AutorizzazioniAdmin permessi,
                                           com.eligesoft.orditogram.accesso.AccessoEsterni esterni,
                                           @Value("${app.login.locale:true}") boolean loginLocale) throws Exception {
        // CSRF: il token di Spring non serve perché le richieste che modificano dati da un browser devono avere
        // l'intestazione X-Requested-With, che un altro sito non può aggiungere (vedi FiltroRichiesteSessione).
        http.csrf(csrf -> csrf.disable())  // NOSONAR: protezione CSRF con l'intestazione personalizzata, sotto
            .addFilterAfter(new FiltroRichiesteSessione(), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // pagina di errore di Spring (es. 404): risponde con il codice giusto anche a chi non è autenticato
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/metodi", "/api/info").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                .requestMatchers("/api/messaggi/**").permitAll()  // sistemi esterni: si autenticano con X-API-Key (app.messaggi.chiavi)
                .requestMatchers("/api/demo/**").permitAll()      // servizi di prova chiamati dai processi (app.demo.servizi)
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Studio e amministrazione: ogni sezione con il suo permesso (accesso/Permesso), letto dal database
                .requestMatchers("/api/admin/users/**", "/api/admin/ruoli/**", "/api/admin/strutture/**").access(permessi.uno(Permesso.UTENTI))
                .requestMatchers("/api/admin/organizzazioni/**").access(permessi.uno(Permesso.ORGANIZZAZIONI))
                .requestMatchers(HttpMethod.GET, "/api/admin/forms/**").access(permessi.uno(Permesso.STUDIO_FORM, Permesso.STUDIO_PROCESSI))
                .requestMatchers("/api/admin/forms/**").access(permessi.uno(Permesso.STUDIO_FORM))
                .requestMatchers(HttpMethod.GET, "/api/admin/gruppi").access(permessi.uno(Permesso.STUDIO_PROCESSI, Permesso.STUDIO_FORM, Permesso.UTENTI))
                .requestMatchers(HttpMethod.POST, "/api/admin/processes/deploy").access(permessi.tutti(Permesso.STUDIO_PROCESSI, Permesso.STUDIO_PUBBLICA))
                // un pacchetto importato contiene processi e form
                .requestMatchers(HttpMethod.POST, "/api/admin/pacchetti/**").access(permessi.tutti(Permesso.STUDIO_PROCESSI, Permesso.STUDIO_FORM))
                .requestMatchers("/api/admin/processes/**", "/api/admin/bozze/**", "/api/admin/pacchetti/**",
                    "/api/admin/connettori/**", "/api/admin/credenziali/**").access(permessi.uno(Permesso.STUDIO_PROCESSI))
                .requestMatchers("/api/admin/integrazioni/**").access(permessi.uno(Permesso.INTEGRAZIONI))
                .requestMatchers(HttpMethod.GET, "/api/admin/registro/**").access(permessi.uno(Permesso.REGISTRO))
                // l'elenco e la definizione dei tipi servono anche a chi disegna form e processi (campi anagrafica);
                // le regole di accesso (/permessi) e le modifiche restano a chi definisce le anagrafiche
                .requestMatchers(HttpMethod.GET, "/api/admin/anagrafiche", "/api/admin/anagrafiche/*")
                    .access(permessi.uno(Permesso.ANAGRAFICHE_DEFINISCI, Permesso.STUDIO_FORM, Permesso.STUDIO_PROCESSI))
                .requestMatchers("/api/admin/anagrafiche/**").access(permessi.uno(Permesso.ANAGRAFICHE_DEFINISCI))
                // tutto il resto (sezioni nuove comprese): solo chi ha ogni permesso
                .requestMatchers("/api/admin/**").access(permessi.tutti())
                // tutto il resto: utenti autenticati; gli utenti esterni solo le API del portale (accesso/AccessoEsterni)
                .anyRequest().access(esterni.autenticati()))
            // le API rispondono 401 (mai il popup del browser né un redirect): ci pensa il frontend
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/")));

        // nome utente e password di Orditogram (frontend e integrazioni); si spegne con app.login.locale=false
        if (loginLocale) http.httpBasic(Customizer.withDefaults());

        ClientRegistrationRepository repo = registrazioni.getIfAvailable();
        if (repo != null) {
            SimpleUrlAuthenticationSuccessHandler ok = new SimpleUrlAuthenticationSuccessHandler(oidc.getDopoLogin());
            ok.setAlwaysUseDefaultTargetUrl(true);
            OidcClientInitiatedLogoutSuccessHandler uscita = new OidcClientInitiatedLogoutSuccessHandler(repo);
            uscita.setPostLogoutRedirectUri("{baseUrl}" + oidc.getDopoLogin());
            uscita.setDefaultTargetUrl(oidc.getDopoLogin());
            http.oauth2Login(o -> o
                    .userInfoEndpoint(u -> u.oidcUserService(oidcUserService.getObject()))
                    .successHandler(ok)
                    .failureHandler(new SimpleUrlAuthenticationFailureHandler(oidc.getDopoLogin() + "?errore=accesso")))
                .logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler(uscita));
        } else {
            SimpleUrlLogoutSuccessHandler uscita = new SimpleUrlLogoutSuccessHandler();
            uscita.setDefaultTargetUrl(oidc.getDopoLogin());
            http.logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler(uscita));
        }
        return http.build();
    }

    /**
     * Protezione CSRF. Le richieste all'API che modificano dati e arrivano da un browser devono avere l'intestazione
     * X-Requested-With: un modulo o uno script di un altro sito non può aggiungerla senza il permesso CORS.
     * Da un browser = sessione del provider, oppure cookie, Origin o Sec-Fetch-Site, che i browser mandano da soli
     * (anche con le credenziali Basic ricordate). Le integrazioni server-to-server con Basic non li mandano.
     */
    static class FiltroRichiesteSessione extends OncePerRequestFilter {
        private static final Set<String> SICURI = Set.of("GET", "HEAD", "OPTIONS");

        static boolean daBrowser(HttpServletRequest req) {
            var autenticazione = SecurityContextHolder.getContext().getAuthentication();
            // chi non ha fatto l'accesso (es. sistemi esterni su /api/messaggi con la loro chiave) non ha nulla da rubare
            if (autenticazione == null || !autenticazione.isAuthenticated()
                || autenticazione instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) return false;
            return autenticazione instanceof OAuth2AuthenticationToken
                || req.getHeader("Cookie") != null || req.getHeader("Origin") != null || req.getHeader("Sec-Fetch-Site") != null;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            boolean api = req.getRequestURI().startsWith(req.getContextPath() + "/api/");
            if (api && !SICURI.contains(req.getMethod()) && req.getHeader("X-Requested-With") == null && daBrowser(req)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Richiesta non consentita");
                return;
            }
            chain.doFilter(req, res);
        }
    }
}
