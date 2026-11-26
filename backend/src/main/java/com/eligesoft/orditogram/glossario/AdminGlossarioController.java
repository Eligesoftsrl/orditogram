package com.eligesoft.orditogram.glossario;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Studio → Glossario: le parole dell'installazione per ente, ufficio e utenti esterni.
 * Solo per l'amministratore (regola generale di /api/admin/** in SecurityConfig). La lettura per tutti è in /api/info.
 */
@RestController
@RequestMapping("/api/admin/glossario")
public class AdminGlossarioController {

    private final GlossarioService glossario;

    public AdminGlossarioController(GlossarioService glossario) {
        this.glossario = glossario;
    }

    @GetMapping
    public List<GlossarioService.Parola> termini() {
        return glossario.termini();
    }

    @PutMapping
    public List<GlossarioService.Parola> salva(@RequestBody Map<String, Map<String, String>> termini) {
        return glossario.salva(termini);
    }
}
