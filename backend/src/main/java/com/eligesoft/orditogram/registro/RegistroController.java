package com.eligesoft.orditogram.registro;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Consultazione del registro (solo amministratori: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/registro")
public class RegistroController {

    private final RegistroAzioneRepository repo;
    private final RegistroLetturaRepository letture;
    private final RegistroErroreRepository errori;

    public RegistroController(RegistroAzioneRepository repo, RegistroLetturaRepository letture, RegistroErroreRepository errori) {
        this.repo = repo;
        this.letture = letture;
        this.errori = errori;
    }

    private static PageRequest pagina(int pagina, int dimensione) {
        return PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(1000, dimensione)));
    }

    private static String vuoto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Map<String, Object> pagina(Page<?> p, java.util.List<Map<String, Object>> righe) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("righe", righe);
        out.put("totale", p.getTotalElements());
        out.put("pagina", p.getNumber());
        out.put("dimensione", p.getSize());
        return out;
    }

    /**
     * Chi ha aperto pratiche, attività, documenti e schede. Filtri: tipo, esito, utente, oggetto (es. l'id della
     * pratica: "chi ha visto questa pratica?") e periodo (date ISO, estremo finale escluso).
     */
    @GetMapping("/letture")
    public Map<String, Object> letture(@RequestParam(required = false) String azione,
                                       @RequestParam(required = false) String esito,
                                       @RequestParam(required = false) String utente,
                                       @RequestParam(required = false) String oggetto,
                                       @RequestParam(required = false) java.time.LocalDate dal,
                                       @RequestParam(required = false) java.time.LocalDate al,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "50") int dimensione) {
        java.time.ZoneId zona = java.time.ZoneId.systemDefault();
        java.time.OffsetDateTime da = dal != null ? dal.atStartOfDay(zona).toOffsetDateTime()
            : java.time.OffsetDateTime.parse("1970-01-01T00:00:00Z");
        java.time.OffsetDateTime a = al != null ? al.plusDays(1).atStartOfDay(zona).toOffsetDateTime()
            : java.time.OffsetDateTime.parse("9999-12-31T00:00:00Z");
        String chi = vuoto(utente);
        Page<RegistroLettura> p = letture.cerca(vuoto(azione), vuoto(esito), chi == null ? null : chi.toLowerCase(Locale.ROOT),
            vuoto(oggetto), da, a, pagina(pagina, dimensione));
        return pagina(p, p.getContent().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("quando", r.getQuando());
            m.put("utente", r.getUtente());
            m.put("azione", r.getAzione());
            m.put("oggetto", r.getOggetto());
            m.put("dettagli", r.getDettagli());
            m.put("esito", r.getEsito());
            m.put("indirizzo", r.getIndirizzo());
            return m;
        }).toList());
    }

    /** Errori imprevisti e lavori del motore falliti; q cerca anche il codice che la persona ha ricevuto. */
    @GetMapping("/errori")
    public Map<String, Object> errori(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String origine,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "50") int dimensione) {
        String testo = vuoto(q) == null ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Page<RegistroErrore> p = errori.cerca(vuoto(origine), testo, pagina(pagina, dimensione));
        return pagina(p, p.getContent().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("quando", r.getQuando());
            m.put("codice", r.getCodice());
            m.put("origine", r.getOrigine());
            m.put("utente", r.getUtente());
            m.put("richiesta", r.getRichiesta());
            m.put("tipo", r.getTipo());
            m.put("messaggio", r.getMessaggio());
            m.put("traccia", r.getTraccia());
            m.put("indirizzo", r.getIndirizzo());
            return m;
        }).toList());
    }

    @GetMapping
    public Map<String, Object> cerca(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) String azione,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "50") int dimensione) {
        String testo = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        String tipo = azione == null || azione.isBlank() ? null : azione.trim();
        Page<RegistroAzione> p = repo.cerca(tipo, testo,
            PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(1000, dimensione))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("righe", p.getContent().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("quando", r.getQuando());
            m.put("utente", r.getUtente());
            m.put("utenteNome", r.getUtenteNome());
            m.put("azione", r.getAzione());
            m.put("oggetto", r.getOggetto());
            m.put("dettagli", r.getDettagli());
            m.put("indirizzo", r.getIndirizzo());
            return m;
        }).toList());
        out.put("totale", p.getTotalElements());
        out.put("pagina", p.getNumber());
        out.put("dimensione", p.getSize());
        return out;
    }
}
