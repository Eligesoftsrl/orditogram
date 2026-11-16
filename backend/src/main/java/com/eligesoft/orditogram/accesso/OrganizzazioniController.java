package com.eligesoft.orditogram.accesso;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le organizzazioni esterne attive, per scegliere una parte nei campi Organizzazione dei form.
 * Per il personale; gli utenti esterni non possono sfogliare l'anagrafica (vedi AccessoEsterni).
 */
@RestController
@RequestMapping("/api/organizzazioni")
public class OrganizzazioniController {

    private static final int MAX_RISULTATI = 50;

    private final OrganizzazioneRepository organizzazioni;

    public OrganizzazioniController(OrganizzazioneRepository organizzazioni) {
        this.organizzazioni = organizzazioni;
    }

    /** q: parte del nome, del codice o del codice fiscale; al massimo 50 risultati in ordine di nome. */
    @GetMapping
    public List<Map<String, Object>> cerca(@RequestParam(required = false) String q) {
        String cerca = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return organizzazioni.findByTipoOrderByNomeAsc(Organizzazione.ESTERNA).stream()
            .filter(Organizzazione::isAttiva)
            .filter(o -> cerca.isEmpty() || o.getNome().toLowerCase(Locale.ROOT).contains(cerca)
                || o.getCodice().contains(cerca)
                || (o.getCodiceFiscale() != null && o.getCodiceFiscale().toLowerCase(Locale.ROOT).contains(cerca)))
            .limit(MAX_RISULTATI)
            .map(o -> Map.<String, Object>of("id", o.getId(), "nome", o.getNome(),
                "codiceFiscale", o.getCodiceFiscale() == null ? "" : o.getCodiceFiscale()))
            .toList();
    }
}
