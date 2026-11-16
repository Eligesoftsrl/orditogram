package com.eligesoft.orditogram.anagrafica;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Studio → Anagrafiche: tipi di anagrafica e regole di accesso alle loro schede.
 * Serve il permesso anagrafiche.definisci (SecurityConfig), che non dà accesso ai dati delle schede.
 */
@RestController
@RequestMapping("/api/admin/anagrafiche")
public class AdminAnagraficheController {

    private final TipiAnagraficaService tipi;

    public AdminAnagraficheController(TipiAnagraficaService tipi) {
        this.tipi = tipi;
    }

    @GetMapping
    public List<TipiAnagraficaService.Tipo> elenco() {
        return tipi.elenco();
    }

    @GetMapping("/{codice}")
    public TipiAnagraficaService.Tipo leggi(@PathVariable String codice) {
        return tipi.leggi(codice);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TipiAnagraficaService.Tipo crea(@RequestBody TipiAnagraficaService.Tipo tipo) {
        return tipi.crea(tipo);
    }

    @PutMapping("/{codice}")
    public TipiAnagraficaService.Tipo modifica(@PathVariable String codice, @RequestBody TipiAnagraficaService.Tipo tipo) {
        return tipi.modifica(codice, tipo);
    }

    @DeleteMapping("/{codice}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void elimina(@PathVariable String codice) {
        tipi.elimina(codice);
    }

    @GetMapping("/{codice}/permessi")
    public List<TipiAnagraficaService.Regola> regole(@PathVariable String codice) {
        return tipi.regole(codice);
    }

    @PutMapping("/{codice}/permessi")
    public List<TipiAnagraficaService.Regola> sostituisciRegole(@PathVariable String codice,
                                                                @RequestBody List<TipiAnagraficaService.Regola> regole) {
        return tipi.sostituisciRegole(codice, regole);
    }
}
