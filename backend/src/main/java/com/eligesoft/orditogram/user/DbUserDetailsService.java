package com.eligesoft.orditogram.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Carica gli utenti da Postgres per Spring Security.
 * Activiti legge utente e gruppi da qui:
 *   ROLE_ACTIVITI_USER -> può usare ProcessRuntime / TaskRuntime
 *   GROUP_<nome>       -> candidateGroups="<nome>" nel BPMN
 * I permessi applicativi (ruoli di Orditogram) non sono qui: si leggono dal database a ogni controllo
 * (accesso/PermessiService), così valgono subito anche per chi ha già una sessione.
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final AppUserRepository repo;

    public DbUserDetailsService(AppUserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = repo.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + username));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ACTIVITI_USER"));
        u.gruppiEffettivi().forEach(g -> authorities.add(new SimpleGrantedAuthority("GROUP_" + g)));

        return User.withUsername(u.getUsername())
            .password(u.getPassword())
            .disabled(!u.isEnabled())
            .authorities(authorities)
            .build();
    }
}
