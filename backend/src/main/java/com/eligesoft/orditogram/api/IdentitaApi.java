package com.eligesoft.orditogram.api;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Il sistema esterno che sta chiamando l'API, riconosciuto dalla chiave.
 * Il nome dell'autenticazione è "api:<nome>": così lo storico delle pratiche mostra quale sistema ha agito.
 */
public class IdentitaApi extends AbstractAuthenticationToken {

    public static final String PREFISSO = "api:";

    private final Long chiaveId;
    private final String sistema;
    private final Set<String> ambiti;
    private final Set<String> processi;

    public IdentitaApi(Long chiaveId, String sistema, Set<String> ambiti, Set<String> processi) {
        super(ambiti.stream().map(a -> new SimpleGrantedAuthority("SCOPE_" + a)).collect(Collectors.toSet()));
        this.chiaveId = chiaveId;
        this.sistema = sistema;
        this.ambiti = Set.copyOf(ambiti);
        this.processi = Set.copyOf(processi);
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return ""; }
    @Override public Object getPrincipal() { return PREFISSO + sistema; }
    @Override public String getName() { return PREFISSO + sistema; }

    public Long chiaveId() { return chiaveId; }
    public String sistema() { return sistema; }
    public Set<String> ambiti() { return ambiti; }
    /** Vuoto = tutti i processi. */
    public Set<String> processi() { return processi; }

    public boolean puo(String ambito) { return ambiti.contains(ambito); }

    public boolean processoConsentito(String chiaveProcesso) {
        return processi.isEmpty() || processi.contains(chiaveProcesso);
    }
}
