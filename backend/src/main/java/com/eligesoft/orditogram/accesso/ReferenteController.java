package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Portale → La mia organizzazione: il referente gestisce le persone della sua organizzazione.
 * Aperto agli utenti esterni (accesso/AccessoEsterni); ogni richiesta verifica che chi agisce sia referente.
 */
@RestController
@RequestMapping("/api/organizzazione")
public class ReferenteController {

    public record Gestita(Long id, String codice, String nome, List<ReferentiService.Collega> persone) { }

    public record NuovaPersona(String nome, String cognome, String email) { }

    private final ReferentiService referenti;
    private final CurrentUserService currentUser;

    public ReferenteController(ReferentiService referenti, CurrentUserService currentUser) {
        this.referenti = referenti;
        this.currentUser = currentUser;
    }

    /** Le organizzazioni che gestisco, con le loro persone (vuoto se non sono referente). */
    @GetMapping
    public List<Gestita> mie() {
        return referenti.gestite(currentUser.current()).stream()
            .map(o -> new Gestita(o.getId(), o.getCodice(), o.getNome(), referenti.colleghi(o))).toList();
    }

    @PostMapping("/{orgId}/persone")
    public ReferentiService.Aggiunta aggiungi(@PathVariable Long orgId, @RequestBody NuovaPersona p) {
        return referenti.aggiungi(referenti.verificaReferente(orgId), p.nome(), p.cognome(), p.email());
    }

    @PostMapping("/{orgId}/persone/{id}/disattiva")
    public ReferentiService.Collega disattiva(@PathVariable Long orgId, @PathVariable Long id) {
        return referenti.attiva(referenti.verificaReferente(orgId), id, false);
    }

    @PostMapping("/{orgId}/persone/{id}/riattiva")
    public ReferentiService.Collega riattiva(@PathVariable Long orgId, @PathVariable Long id) {
        return referenti.attiva(referenti.verificaReferente(orgId), id, true);
    }

    @DeleteMapping("/{orgId}/persone/{id}")
    public void togli(@PathVariable Long orgId, @PathVariable Long id) {
        referenti.togli(referenti.verificaReferente(orgId), id);
    }
}
