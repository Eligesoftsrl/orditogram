package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.anagrafica.SchedeService;
import com.eligesoft.orditogram.api.ErroriApi.ErroreApi;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;

/**
 * API pubblica, anagrafiche: un sistema esterno cerca, legge, crea e modifica le schede dei tipi elencati nella sua
 * chiave, con i permessi anagrafiche:leggi e anagrafiche:scrivi. Le regole sono quelle dell'interfaccia
 * (anagrafica/SchedeService): validazione dei dati, chiavi uniche, versione letta per modificare, niente campi riservati
 * ai ruoli. Un tipo non elencato nella chiave, per il sistema, non esiste (404).
 */
@RestController
@RequestMapping("/api/v1/anagrafiche")
public class ApiV1AnagraficheController {

    /** Corpo di creazione e modifica: per modificare serve la versione letta. */
    public record RichiestaScheda(Long versione, Map<String, Object> dati, String nota) { }

    private final SchedeService schede;

    public ApiV1AnagraficheController(SchedeService schede) {
        this.schede = schede;
    }

    @GetMapping
    public List<Map<String, Object>> tipi() {
        richiediLettura();
        return schede.tipi().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codice", t.codice());
            m.put("nome", t.nome());
            m.put("descrizione", t.descrizione());
            m.put("attiva", t.attivo());
            m.put("campi", t.definizione().path("fields"));
            return m;
        }).toList();
    }

    @GetMapping("/{tipo}/schede")
    public Map<String, Object> cerca(@PathVariable String tipo,
                                     @RequestParam(required = false) String testo,
                                     @RequestParam(required = false) String campo,
                                     @RequestParam(required = false) String valore,
                                     @RequestParam(required = false) String stato,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "50") int dimensione) {
        richiediLettura();
        SchedeService.Pagina p = schede.cerca(tipo, testo, campo, valore, stato, pagina, dimensione);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("righe", p.schede().stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", r.id());
            x.put("titolo", r.titolo());
            x.put("stato", r.stato());
            x.put("modificataIl", r.modificataIl());
            return x;
        }).toList());
        m.put("totale", p.totale());
        m.put("pagina", p.pagina());
        m.put("dimensione", p.dimensione());
        return m;
    }

    @GetMapping("/{tipo}/schede/{id}")
    public Map<String, Object> leggi(@PathVariable String tipo, @PathVariable Long id) {
        richiediLettura();
        return scheda(schede.leggi(tipo, id));
    }

    @PostMapping("/{tipo}/schede")
    public ResponseEntity<Map<String, Object>> crea(@PathVariable String tipo, @RequestBody(required = false) RichiestaScheda r) {
        richiedi(Ambiti.ANAGRAFICHE_SCRIVI);
        if (r == null || r.dati() == null) throw ErroriApi.richiesta("Indica i dati della scheda (\"dati\")");
        SchedeService.Scheda s = schede.crea(tipo, r.dati());
        return ResponseEntity.status(201)
            .location(ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/anagrafiche/{tipo}/schede/{id}")
                .buildAndExpand(tipo, s.id()).toUri())
            .body(scheda(s));
    }

    @PutMapping("/{tipo}/schede/{id}")
    public Map<String, Object> modifica(@PathVariable String tipo, @PathVariable Long id, @RequestBody(required = false) RichiestaScheda r) {
        richiedi(Ambiti.ANAGRAFICHE_SCRIVI);
        if (r == null || r.dati() == null) throw ErroriApi.richiesta("Indica i dati della scheda (\"dati\") e la versione letta (\"versione\")");
        return scheda(schede.modifica(tipo, id, r.versione(), r.dati(), r.nota()));
    }

    /** La scheda come la vede un sistema esterno: dati, collegamenti (solo numero, tipo e titolo se visibile). */
    private static Map<String, Object> scheda(SchedeService.Scheda s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id());
        m.put("tipo", s.tipo());
        m.put("titolo", s.titolo());
        m.put("stato", s.stato());
        m.put("versione", s.versione());
        m.put("dati", s.dati());
        Map<String, Object> collegamenti = new LinkedHashMap<>();
        (s.collegamenti() == null ? Map.<String, SchedeService.Collegamento>of() : s.collegamenti()).forEach((campo, c) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", c.id());
            x.put("tipo", c.tipo());
            if (c.visibile()) { x.put("titolo", c.titolo()); x.put("stato", c.stato()); }
            collegamenti.put(campo, x);
        });
        m.put("collegamenti", collegamenti);
        m.put("creataIl", s.creataIl());
        m.put("modificataIl", s.modificataIl());
        return m;
    }

    private static IdentitaApi identita() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof IdentitaApi id) return id;
        throw new ErroreApi(401, "chiave_mancante", "Serve una chiave API");
    }

    private static void richiedi(String ambito) {
        if (!identita().puo(ambito)) throw ErroriApi.permesso(ambito);
    }

    /** Per leggere basta uno dei due permessi: chi scrive le schede le legge anche. */
    private static void richiediLettura() {
        IdentitaApi id = identita();
        if (!id.puo(Ambiti.ANAGRAFICHE_LEGGI) && !id.puo(Ambiti.ANAGRAFICHE_SCRIVI)) throw ErroriApi.permesso(Ambiti.ANAGRAFICHE_LEGGI);
    }
}
