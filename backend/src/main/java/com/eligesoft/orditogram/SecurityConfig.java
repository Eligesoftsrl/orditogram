package com.eligesoft.orditogram;

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
            .csrf(csrf -> csrf.disable())
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
                                           @Value("${app.login.locale:true}") boolean loginLocale) throws Exception {
        http.csrf(csrf -> csrf.disable())
            // con la sessione del provider: le richieste che modificano dati devono arrivare dal frontend di Orditogram
            // (intestazione X-Requested-With, che un altro sito non può aggiungere): protezione CSRF
            .addFilterAfter(new FiltroRichiesteSessione(), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // pagina di errore di Spring (es. 404): risponde con il codice giusto anche a chi non è autenticato
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/metodi", "/api/info").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                .requestMatchers("/api/messaggi/**").permitAll()  // sistemi esterni: si autenticano con X-API-Key (app.messaggi.chiavi)
                .requestMatchers("/api/demo/**").permitAll()      // servizi di prova chiamati dai processi (app.demo.servizi)
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
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
     * Se l'utente è autenticato con la sessione del provider (cookie), le richieste che modificano dati
     * devono avere l'intestazione X-Requested-With: un modulo o uno script di un altro sito non può aggiungerla.
     */
    static class FiltroRichiesteSessione extends OncePerRequestFilter {
        private static final Set<String> SICURI = Set.of("GET", "HEAD", "OPTIONS");

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            boolean sessione = SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken;
            boolean api = req.getRequestURI().startsWith(req.getContextPath() + "/api/");
            if (sessione && api && !SICURI.contains(req.getMethod()) && req.getHeader("X-Requested-With") == null) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Richiesta non consentita");
                return;
            }
            chain.doFilter(req, res);
        }
    }
}
