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
 *  - gli utenti disattivati niente;
 *  - gli utenti esterni solo SCEGLIE, se una regola lo dà a un loro gruppo ("esterni", "org:&lt;id&gt;"): cercano per
 *    nome e scelgono nei form, senza mai leggere i dati delle schede;
 *  - gli altri hanno le azioni delle regole che valgono per i loro gruppi o ruoli; modifica ed esporta comprendono vede,
 *    vede comprende sceglie;
 *  - un sistema esterno (chiave dell'API pubblica) ha le azioni che la chiave dà sui tipi elencati in essa.
 * Il permesso anagrafiche.definisci (Studio) non dà accesso ai dati delle schede.
 */
@Service
public class AccessoAnagrafiche {

    public static final Set<String> TUTTE = Set.of(PermessoAnagrafica.VEDE, PermessoAnagrafica.MODIFICA,
        PermessoAnagrafica.ESPORTA, PermessoAnagrafica.SCEGLIE);

    /**
     * Chi agisce, calcolato una volta per richiesta: un utente con i suoi ruoli, oppure un sistema esterno (sistema non
     * nullo) con le azioni per codice di tipo date dalla sua chiave API.
     */
    public record Utente(AppUser utente, Set<String> ruoli, boolean amministratore, String sistema,
                         Map<String, Set<String>> azioniSistema) {
        public Utente(AppUser utente, Set<String> ruoli, boolean amministratore) {
            this(utente, ruoli, amministratore, null, Map.of());
        }
        public boolean api() { return sistema != null; }
        /** Il nome che va nelle versioni e nel registro: l'utente, o "api:&lt;sistema&gt;". */
        public String username() { return api() ? "api:" + sistema : utente != null ? utente.getUsername() : null; }
        public boolean esterno() { return !api() && utente != null && utente.isEsterno(); }
        public boolean escluso() { return !api() && (utente == null || !utente.isEnabled()); }
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

    /**
     * Un sistema esterno: leggi dà vede (e sceglie), scrivi dà anche modifica, sui tipi elencati nella chiave.
     * Mai esporta né i campi riservati ai ruoli.
     */
    public static Utente sistema(String nome, Set<String> tipi, boolean leggi, boolean scrivi) {
        Set<String> azioni = new TreeSet<>();
        if (leggi || scrivi) { azioni.add(PermessoAnagrafica.VEDE); azioni.add(PermessoAnagrafica.SCEGLIE); }
        if (scrivi) azioni.add(PermessoAnagrafica.MODIFICA);
        Map<String, Set<String>> perTipo = new HashMap<>();
        if (!azioni.isEmpty()) for (String t : tipi) perTipo.put(t, Set.copyOf(azioni));
        return new Utente(null, Set.of(), false, nome, Map.copyOf(perTipo));
    }

    /** Le azioni dell'utente su ogni tipo, per id (solo i tipi su cui ne ha almeno una). Non per i sistemi esterni. */
    @Transactional(readOnly = true)
    public Map<Long, Set<String>> azioni(Utente u) {
        Map<Long, Set<String>> out = new HashMap<>();
        if (u.escluso() || u.api()) return out;
        List<PermessoAnagrafica> mie = new ArrayList<>();
        Set<String> gruppi = u.utente().gruppiEffettivi();
        if (!gruppi.isEmpty()) mie.addAll(regole.findByTipoSoggettoAndSoggettoIn(PermessoAnagrafica.GRUPPO, gruppi));
        if (!u.esterno() && !u.ruoli().isEmpty()) mie.addAll(regole.findByTipoSoggettoAndSoggettoIn(PermessoAnagrafica.RUOLO, u.ruoli()));
        for (PermessoAnagrafica r : mie) {
            // un utente esterno non legge mai le schede: delle regole vale solo "sceglie"
            if (u.esterno() && !PermessoAnagrafica.SCEGLIE.equals(r.getAzione())) continue;
            Set<String> a = out.computeIfAbsent(r.getTipoId(), k -> new TreeSet<>());
            a.add(r.getAzione());
            a.add(PermessoAnagrafica.SCEGLIE);
            if (!PermessoAnagrafica.SCEGLIE.equals(r.getAzione())) a.add(PermessoAnagrafica.VEDE);
        }
        return out;
    }

    /** Le azioni dell'utente (o del sistema esterno) su un tipo. */
    @Transactional(readOnly = true)
    public Set<String> azioni(Utente u, TipoAnagrafica tipo) {
        if (u.api()) return u.azioniSistema().getOrDefault(tipo.getCodice(), Set.of());
        if (u.escluso()) return Set.of();
        if (u.amministratore()) return TUTTE;
        return azioni(u).getOrDefault(tipo.getId(), Set.of());
    }

    public boolean puo(Utente u, TipoAnagrafica tipo, String azione) {
        return azioni(u, tipo).contains(azione);
    }
}
