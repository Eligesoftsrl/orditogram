package com.eligesoft.orditogram.modulo;

import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Quali moduli sono accesi. Si legge a ogni uso (poche righe, nessuna cache): spegnere o accendere vale subito.
 * Spegnere un modulo non cancella niente: i dati già salvati restano, cambiano solo le regole da qui in avanti.
 */
@Service
public class ModuliService {

    public record Stato(String codice, String nome, String descrizione, List<String> porta, boolean acceso,
                        OffsetDateTime spentoIl, String spentoDa) { }

    private final ModuloSpentoRepository spenti;
    private final RegistroService registro;
    private final CurrentUserService currentUser;

    public ModuliService(ModuloSpentoRepository spenti, RegistroService registro, CurrentUserService currentUser) {
        this.spenti = spenti;
        this.registro = registro;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public boolean acceso(Modulo m) {
        return !spenti.existsById(m.codice());
    }

    /** Codici dei moduli accesi, per l'interfaccia (/api/info). */
    @Transactional(readOnly = true)
    public List<String> accesi() {
        Set<String> off = new HashSet<>();
        spenti.findAll().forEach(s -> off.add(s.getCodice()));
        return Arrays.stream(Modulo.values()).map(Modulo::codice).filter(c -> !off.contains(c)).toList();
    }

    @Transactional(readOnly = true)
    public List<Stato> stati() {
        Map<String, ModuloSpento> off = new HashMap<>();
        spenti.findAll().forEach(s -> off.put(s.getCodice(), s));
        return Arrays.stream(Modulo.values()).map(m -> {
            ModuloSpento s = off.get(m.codice());
            return new Stato(m.codice(), m.nome(), m.descrizione(), m.porta(), s == null,
                s != null ? s.getSpentoIl() : null, s != null ? s.getSpentoDa() : null);
        }).toList();
    }

    /** Accende o spegne un modulo; va nel registro. */
    @Transactional
    public List<Stato> imposta(String codice, boolean acceso) {
        Modulo m = Modulo.da(codice).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modulo sconosciuto: " + codice));
        boolean ora = acceso(m);
        if (ora == acceso) return stati();
        if (acceso) spenti.deleteById(m.codice());
        else spenti.save(new ModuloSpento(m.codice(), OffsetDateTime.now(), currentUser.username()));
        registro.registra(acceso ? "MODULO_ACCESO" : "MODULO_SPENTO", m.codice(), m.nome());
        return stati();
    }
}
