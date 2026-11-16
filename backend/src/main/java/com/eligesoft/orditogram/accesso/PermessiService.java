package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Che cosa può fare un utente: l'unione dei permessi dei ruoli assegnati a lui e ai suoi gruppi.
 * Si legge dal database a ogni controllo, così un ruolo tolto vale subito, anche per chi ha una sessione aperta.
 * Per default nessun permesso: chi non ha ruoli lavora solo le sue attività e pratiche.
 */
@Service
public class PermessiService {

    private final AppUserRepository utenti;
    private final RuoloRepository ruoli;

    public PermessiService(AppUserRepository utenti, RuoloRepository ruoli) {
        this.utenti = utenti;
        this.ruoli = ruoli;
    }

    @Transactional(readOnly = true)
    public Set<Permesso> permessiDi(String username) {
        if (username == null) return EnumSet.noneOf(Permesso.class);
        return utenti.findByUsername(username).map(this::permessiDi).orElse(EnumSet.noneOf(Permesso.class));
    }

    @Transactional(readOnly = true)
    public Set<Permesso> permessiDi(AppUser u) {
        Set<Permesso> permessi = EnumSet.noneOf(Permesso.class);
        // gli utenti esterni non hanno mai permessi di amministrazione, qualunque ruolo o gruppo abbiano
        if (!u.isEnabled() || u.isEsterno()) return permessi;
        for (Ruolo r : ruoliDi(u)) permessi.addAll(permessiDel(r));
        return permessi;
    }

    /** Ruoli dell'utente: quelli assegnati a lui e quelli dei suoi gruppi, senza ripetizioni, in ordine di nome. */
    @Transactional(readOnly = true)
    public List<Ruolo> ruoliDi(AppUser u) {
        Map<String, Ruolo> tutti = new LinkedHashMap<>();
        if (u.isEsterno()) return List.of();
        u.getRuoli().forEach(r -> tutti.put(r.getCodice(), r));
        if (!u.getGroups().isEmpty()) ruoli.deiGruppi(u.getGroups()).forEach(r -> tutti.putIfAbsent(r.getCodice(), r));
        return tutti.values().stream().sorted(Comparator.comparing(Ruolo::getNome)).toList();
    }

    /** Permessi di un ruolo: l'amministratore li ha tutti, anche quelli aggiunti dopo. Codici sconosciuti ignorati. */
    public static Set<Permesso> permessiDel(Ruolo r) {
        if (r.isAmministratore()) return EnumSet.allOf(Permesso.class);
        Set<Permesso> permessi = EnumSet.noneOf(Permesso.class);
        r.getPermessi().forEach(c -> Permesso.da(c).ifPresent(permessi::add));
        return permessi;
    }

    public boolean ha(String username, Permesso permesso) {
        return permessiDi(username).contains(permesso);
    }

    /** Ha tutti i permessi: è amministratore (direttamente o tramite un gruppo). */
    public boolean amministratore(String username) {
        return permessiDi(username).containsAll(EnumSet.allOf(Permesso.class));
    }
}
