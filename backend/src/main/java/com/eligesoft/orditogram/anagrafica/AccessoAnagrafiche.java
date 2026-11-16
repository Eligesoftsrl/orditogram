package com.eligesoft.orditogram.anagrafica;

import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.accesso.Ruolo;
import com.eligesoft.orditogram.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Che cosa può fare un utente sulle schede di un tipo di anagrafica, dalle regole dello Studio (PermessoAnagrafica).
 * Unico punto di controllo: ogni lettura e modifica delle schede passa da qui.
 *  - l'amministratore può tutto;
 *  - gli utenti esterni e quelli disattivati niente (vedono solo ciò che è collegato alle loro pratiche);
 *  - gli altri hanno le azioni delle regole che valgono per i loro gruppi o ruoli; modifica ed esporta comprendono vede.
 * Il permesso anagrafiche.definisci (Studio) non dà accesso ai dati delle schede.
 */
@Service
public class AccessoAnagrafiche {

    public static final Set<String> TUTTE = Set.of(PermessoAnagrafica.VEDE, PermessoAnagrafica.MODIFICA, PermessoAnagrafica.ESPORTA);

    /** L'utente con i suoi ruoli, calcolato una volta per richiesta. */
    public record Utente(AppUser utente, Set<String> ruoli, boolean amministratore) {
        public String username() { return utente != null ? utente.getUsername() : null; }
        public boolean escluso() { return utente == null || !utente.isEnabled() || utente.isEsterno(); }
    }

    private final PermessoAnagraficaRepository regole;
    private final PermessiService permessi;

    public AccessoAnagrafiche(PermessoAnagraficaRepository regole, PermessiService permessi) {
        this.regole = regole;
        this.permessi = permessi;
    }

    @Transactional(readOnly = true)
    public Utente utente(AppUser u) {
        if (u == null || u.isEsterno()) return new Utente(u, Set.of(), false);
        Set<String> ruoli = permessi.ruoliDi(u).stream().map(Ruolo::getCodice).collect(Collectors.toSet());
        return new Utente(u, ruoli, permessi.amministratore(u.getUsername()));
    }

    /** Le azioni dell'utente su ogni tipo (solo i tipi su cui ne ha almeno una). */
    @Transactional(readOnly = true)
    public Map<Long, Set<String>> azioni(Utente u) {
        Map<Long, Set<String>> out = new HashMap<>();
        if (u.escluso()) return out;
        List<PermessoAnagrafica> mie = new ArrayList<>();
        Set<String> gruppi = u.utente().gruppiEffettivi();
        if (!gruppi.isEmpty()) mie.addAll(regole.findByTipoSoggettoAndSoggettoIn(PermessoAnagrafica.GRUPPO, gruppi));
        if (!u.ruoli().isEmpty()) mie.addAll(regole.findByTipoSoggettoAndSoggettoIn(PermessoAnagrafica.RUOLO, u.ruoli()));
        for (PermessoAnagrafica r : mie) {
            Set<String> a = out.computeIfAbsent(r.getTipoId(), k -> new TreeSet<>());
            a.add(r.getAzione());
            a.add(PermessoAnagrafica.VEDE);
        }
        return out;
    }

    /** Le azioni dell'utente su un tipo. */
    @Transactional(readOnly = true)
    public Set<String> azioni(Utente u, TipoAnagrafica tipo) {
        if (u.escluso()) return Set.of();
        if (u.amministratore()) return TUTTE;
        return azioni(u).getOrDefault(tipo.getId(), Set.of());
    }

    public boolean puo(Utente u, TipoAnagrafica tipo, String azione) {
        return azioni(u, tipo).contains(azione);
    }
}
