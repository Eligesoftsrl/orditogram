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

    public RegistroController(RegistroAzioneRepository repo) {
        this.repo = repo;
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
