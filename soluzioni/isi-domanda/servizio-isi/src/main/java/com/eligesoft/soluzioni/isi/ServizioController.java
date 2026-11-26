package com.eligesoft.soluzioni.isi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.eligesoft.soluzioni.isi.Testi.codiceAsse;
import static com.eligesoft.soluzioni.isi.Testi.testo;

/**
 * API chiamata dai connettori {@code apiConnector} di Orditogram. Ogni risposta è un oggetto JSON piatto con valori
 * di testo: la mappatura {@code dato = campo} li copia nei dati della pratica.
 */
@RestController
public class ServizioController {
    private static final Map<String, String> MODELLI_PERIZIA = Map.of("1.1", "B1.1", "2", "B2", "3", "B3", "4", "B4",
            "5.1", "B5", "5.2", "B5", "1.2", "Dichiarazione sostitutiva B1.2");
    private static final Map<String, String> ESITI = Map.of("Ammetti", "AMMESSA", "Ammetti parzialmente",
            "AMMESSA_PARZIALMENTE", "Respingi", "RESPINTA");

    private final Regole regole;
    private final Registro registro;
    private final ServiziFinti finti;
    private final AvvisoOrditogram avviso;

    public ServizioController(Regole regole, Registro registro, ServiziFinti finti, AvvisoOrditogram avviso) {
        this.regole = regole;
        this.registro = registro;
        this.finti = finti;
        this.avviso = avviso;
    }

    @GetMapping("/salute")
    public Map<String, String> salute() {
        return Registro.mappa("stato", "ok", "versioneRegole", regole.versione());
    }

    @GetMapping("/calendario")
    public Map<String, String> calendario() {
        Map<String, String> c = new LinkedHashMap<>();
        regole.nodo("calendario").fields().forEachRemaining(e -> c.put(e.getKey(), e.getValue().asText()));
        return c;
    }

    @GetMapping("/impresa")
    public Map<String, String> impresa(@RequestParam(defaultValue = "") String codiceFiscale) {
        return finti.anagraficaInail(codiceFiscale);
    }

    @PostMapping("/calcola")
    public Map<String, String> calcola(@RequestBody(required = false) Map<String, Object> d) {
        d = corpo(d);
        Map<String, String> out = Calcolo.calcola(d, regole).inTesto(regole);
        out.put("modelloPerizia", MODELLI_PERIZIA.getOrDefault(codiceAsse(d.get("asse")), ""));
        out.put("gruppoSede", regole.gruppoSede(testo(d.get("regione"))));
        return out;
    }

    @PostMapping("/registra")
    public Map<String, String> registra(@RequestBody(required = false) Map<String, Object> d) {
        return registro.registra(corpo(d));
    }

    @PostMapping("/codice")
    public Map<String, String> codice(@RequestBody(required = false) Map<String, Object> d) {
        return registro.codice(testo(corpo(d).get("numeroDomanda")));
    }

    @PostMapping("/sportello")
    public Map<String, String> sportello(@RequestBody(required = false) Map<String, Object> d) {
        d = corpo(d);
        return registro.sportello(testo(d.get("numeroDomanda")), d.get("codice"));
    }

    @PostMapping("/stato")
    public Map<String, String> stato(@RequestBody(required = false) Map<String, Object> d) {
        d = corpo(d);
        String stato = testo(d.get("stato"));
        if (stato.isEmpty()) stato = ESITI.getOrDefault(testo(d.get("esitoIstruttoria")), "");
        String chi = testo(d.get("chi"));
        return registro.cambiaStato(testo(d.get("numeroDomanda")), stato, chi.isEmpty() ? "sistema" : chi, testo(d.get("motivo")));
    }

    @PostMapping("/elenchi/anteprima")
    public Map<String, String> anteprima(@RequestBody(required = false) Map<String, Object> d,
                                         @RequestParam(required = false) String tipo) {
        return registro.anteprima(tipo(corpo(d), tipo));
    }

    @PostMapping("/elenchi/pubblica")
    public Map<String, String> pubblica(@RequestBody(required = false) Map<String, Object> d,
                                        @RequestParam(required = false) String tipo) {
        return registro.pubblica(tipo(corpo(d), tipo), avviso.avvisatore());
    }

    /** RF-30: pagina pubblica, solo dati pubblicati. */
    @GetMapping("/elenchi")
    public Map<String, List<Map<String, Object>>> elenchi() {
        return Map.of("elenchi", registro.elenchiPubblicati());
    }

    @PostMapping("/istruttoria/{azione}")
    public Map<String, String> istruttoria(@PathVariable String azione, @RequestBody(required = false) Map<String, Object> d) {
        return registro.istruttoria(testo(corpo(d).get("numeroDomanda")), azione);
    }

    @PostMapping("/incarico")
    public Map<String, String> incarico(@RequestBody(required = false) Map<String, Object> d) {
        return ServiziFinti.incaricoTecnico(corpo(d));
    }

    private static Map<String, Object> corpo(Map<String, Object> d) {
        return d == null ? new HashMap<>() : d;
    }

    private static String tipo(Map<String, Object> d, String daIndirizzo) {
        String t = testo(d.get("tipoElenco"));
        return (t.isEmpty() ? testo(daIndirizzo) : t).toUpperCase(Locale.ROOT);
    }
}
