package com.eligesoft.orditogram.modulo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Studio → Moduli: accende e spegne le funzioni di paese e di settore. Solo per l'amministratore (regola generale
 * di /api/admin/** in SecurityConfig). I moduli accesi per tutti sono in /api/info.
 */
@RestController
@RequestMapping("/api/admin/moduli")
public class AdminModuliController {

    private final ModuliService moduli;

    public AdminModuliController(ModuliService moduli) {
        this.moduli = moduli;
    }

    @GetMapping
    public List<ModuliService.Stato> stati() {
        return moduli.stati();
    }

    /** Corpo: {"acceso": true|false}. */
    @PutMapping("/{codice}")
    public List<ModuliService.Stato> imposta(@PathVariable String codice, @RequestBody Map<String, Object> corpo) {
        if (corpo == null || !(corpo.get("acceso") instanceof Boolean acceso))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica \"acceso\": true o false");
        return moduli.imposta(codice, acceso);
    }
}
