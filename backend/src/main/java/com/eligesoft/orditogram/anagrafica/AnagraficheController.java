package com.eligesoft.orditogram.anagrafica;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Le schede delle anagrafiche per il personale: i permessi per tipo li controlla SchedeService a ogni richiesta.
 * Gli utenti esterni non ci arrivano (accesso/AccessoEsterni non elenca queste API).
 * Errori dei dati: 400 { "message", "campi": [ { "campo", "messaggio" } ] }.
 */
@RestController
@RequestMapping("/api/anagrafiche")
public class AnagraficheController {

    /** Dati di una modifica: la versione letta, i dati e una nota facoltativa (es. il motivo della cessazione). */
    public record Richiesta(Long versione, Map<String, Object> dati, String nota) { }

    private final SchedeService schede;
    private final ImportaEsportaSchede importaEsporta;

    public AnagraficheController(SchedeService schede, ImportaEsportaSchede importaEsporta) {
        this.schede = schede;
        this.importaEsporta = importaEsporta;
    }

    @GetMapping
    public List<SchedeService.TipoVisibile> tipi() {
        return schede.tipi();
    }

    @GetMapping("/{tipo}")
    public SchedeService.TipoVisibile tipo(@PathVariable String tipo) {
        return schede.tipo(tipo);
    }

    @GetMapping("/{tipo}/schede")
    public SchedeService.Pagina cerca(@PathVariable String tipo,
                                      @RequestParam(required = false) String testo,
                                      @RequestParam(required = false) String campo,
                                      @RequestParam(required = false) String valore,
                                      @RequestParam(required = false) String stato,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "20") int dimensione) {
        return schede.cerca(tipo, testo, campo, valore, stato, pagina, dimensione);
    }

    @PostMapping("/{tipo}/schede")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedeService.Scheda crea(@PathVariable String tipo, @RequestBody Richiesta richiesta) {
        return schede.crea(tipo, richiesta.dati());
    }

    @GetMapping("/{tipo}/schede/{id}")
    public SchedeService.Scheda leggi(@PathVariable String tipo, @PathVariable Long id) {
        return schede.leggi(tipo, id);
    }

    @PutMapping("/{tipo}/schede/{id}")
    public SchedeService.Scheda modifica(@PathVariable String tipo, @PathVariable Long id, @RequestBody Richiesta richiesta) {
        return schede.modifica(tipo, id, richiesta.versione(), richiesta.dati(), richiesta.nota());
    }

    @PostMapping("/{tipo}/schede/{id}/cessa")
    public SchedeService.Scheda cessa(@PathVariable String tipo, @PathVariable Long id, @RequestBody Richiesta richiesta) {
        return schede.cambiaStato(tipo, id, richiesta.versione(), false, richiesta.nota());
    }

    @PostMapping("/{tipo}/schede/{id}/riattiva")
    public SchedeService.Scheda riattiva(@PathVariable String tipo, @PathVariable Long id, @RequestBody Richiesta richiesta) {
        return schede.cambiaStato(tipo, id, richiesta.versione(), true, richiesta.nota());
    }

    @GetMapping("/{tipo}/schede/{id}/versioni")
    public List<SchedeService.Versione> versioni(@PathVariable String tipo, @PathVariable Long id) {
        return schede.versioni(tipo, id);
    }

    /** Le pratiche collegate alla scheda che l'utente può vedere. */
    @GetMapping("/{tipo}/schede/{id}/pratiche")
    public List<SchedeService.PraticaCollegata> pratiche(@PathVariable String tipo, @PathVariable Long id) {
        return schede.pratiche(tipo, id);
    }

    /** Tutte le schede del tipo, per scaricarle in CSV, Excel o PDF (regola "esporta"; va nel registro). */
    @GetMapping("/{tipo}/esporta")
    public ImportaEsportaSchede.Esportazione esporta(@PathVariable String tipo) {
        return importaEsporta.esporta(tipo);
    }

    /** Importazione da CSV (regola "modifica"): crea le schede nuove e aggiorna quelle con la stessa chiave. */
    @PostMapping(value = "/{tipo}/importa", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportaEsportaSchede.EsitoImport importa(@PathVariable String tipo,
                                                   @RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        return importaEsporta.importa(tipo, file.getBytes(), file.getOriginalFilename());
    }

    @ExceptionHandler(DatiNonValidi.class)
    public ResponseEntity<Map<String, Object>> datiNonValidi(DatiNonValidi e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(),
            "campi", e.errori().stream().map(x -> Map.of("campo", x.campo(), "messaggio", x.messaggio())).toList()));
    }

    /** Due modifiche contemporanee della stessa scheda, o due schede nuove con la stessa chiave nello stesso istante. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, Object>> concorrenza(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
            "La scheda è stata modificata o creata da qualcun altro nello stesso momento: ricarica e riprova"));
    }
}
