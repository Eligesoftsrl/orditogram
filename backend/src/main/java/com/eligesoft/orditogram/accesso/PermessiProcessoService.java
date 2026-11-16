package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Le regole per processo (accesso/PermessoProcesso) applicate a un utente. */
@Service
public class PermessiProcessoService {

    private final PermessoProcessoRepository regole;
    private final AppUserRepository utenti;
    private final PermessiService permessi;
    private final StruttureService strutture;

    public PermessiProcessoService(PermessoProcessoRepository regole, AppUserRepository utenti,
                                   PermessiService permessi, StruttureService strutture) {
        this.regole = regole;
        this.utenti = utenti;
        this.permessi = permessi;
        this.strutture = strutture;
    }

    /** Le regole di un'azione che valgono per l'utente (per i suoi gruppi o i suoi ruoli). */
    @Transactional(readOnly = true)
    public List<PermessoProcesso> regoleDi(AppUser u, String azione) {
        if (!u.isEnabled()) return List.of();
        Set<String> gruppi = u.getGroups();
        Set<String> ruoli = permessi.ruoliDi(u).stream().map(Ruolo::getCodice).collect(Collectors.toSet());
        return regole.findByAzione(azione).stream()
            .filter(r -> PermessoProcesso.GRUPPO.equals(r.getTipoSoggetto()) ? gruppi.contains(r.getSoggetto())
                : PermessoProcesso.RUOLO.equals(r.getTipoSoggetto()) && ruoli.contains(r.getSoggetto()))
            .toList();
    }

    /**
     * Può avviare il processo: se nello Studio ci sono regole "avvia", decidono loro; altrimenti vale il BPMN
     * (candidateStarterGroups/Users, calcolato da chi chiama, perché dipende dalla versione pubblicata).
     */
    @Transactional(readOnly = true)
    public boolean puoAvviare(String username, String processo, Supplier<Boolean> regolaDelBpmn) {
        if (!regole.existsByProcessoAndAzione(processo, PermessoProcesso.AVVIA)) return regolaDelBpmn.get();
        return utenti.findByUsername(username)
            .map(u -> regoleDi(u, PermessoProcesso.AVVIA).stream().anyMatch(r -> r.getProcesso().equals(processo)))
            .orElse(false);
    }

    /** Pratiche che l'utente vede per regola "vede": processi con ambito tutte e quelli limitati alle strutture. */
    public record Visibilita(Set<String> processiTutte, Set<String> processiStruttura, Set<Long> struttureMie,
                             Set<String> processiStrutturaESotto, Set<Long> struttureMieESotto) {
        public boolean vuota() {
            return processiTutte.isEmpty() && processiStruttura.isEmpty() && processiStrutturaESotto.isEmpty();
        }
    }

    @Transactional(readOnly = true)
    public Visibilita visibilita(String username) {
        AppUser u = utenti.findByUsername(username).orElse(null);
        if (u == null) return new Visibilita(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        Set<String> tutte = new HashSet<>();
        Set<String> struttura = new HashSet<>();
        Set<String> strutturaESotto = new HashSet<>();
        for (PermessoProcesso r : regoleDi(u, PermessoProcesso.VEDE)) {
            switch (r.getAmbito()) {
                case PermessoProcesso.TUTTE -> tutte.add(r.getProcesso());
                case PermessoProcesso.STRUTTURA -> struttura.add(r.getProcesso());
                case PermessoProcesso.STRUTTURA_E_SOTTO -> strutturaESotto.add(r.getProcesso());
                default -> { /* ambito sconosciuto: nessun accesso */ }
            }
        }
        Set<Long> mie = new HashSet<>(u.getOrganizzazioni());
        // chi non è in nessuna struttura non vede nulla "per struttura"
        if (mie.isEmpty()) { struttura.clear(); strutturaESotto.clear(); }
        return new Visibilita(tutte, struttura, mie, strutturaESotto,
            strutturaESotto.isEmpty() ? Set.of() : strutture.conDiscendenti(mie));
    }

    /**
     * Processi le cui pratiche l'utente non può esportare: quelli con regole "esporta" che non lo comprendono.
     * L'amministratore esporta tutto.
     */
    @Transactional(readOnly = true)
    public Set<String> nonEsportabili(String username) {
        if (permessi.amministratore(username)) return Set.of();
        Set<String> conRegole = regole.findByAzione(PermessoProcesso.ESPORTA).stream()
            .map(PermessoProcesso::getProcesso).collect(Collectors.toSet());
        if (conRegole.isEmpty()) return Set.of();
        Set<String> mie = utenti.findByUsername(username)
            .map(u -> regoleDi(u, PermessoProcesso.ESPORTA).stream().map(PermessoProcesso::getProcesso).collect(Collectors.toSet()))
            .orElse(Set.of());
        conRegole.removeAll(mie);
        return conRegole;
    }
}
