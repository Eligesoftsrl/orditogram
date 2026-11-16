package com.eligesoft.orditogram.accesso;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Studio → Processi → Accesso: chi avvia, chi vede tutte le pratiche (con l'ambito), chi esporta. */
@RestController
@RequestMapping("/api/admin/processes/{chiave}/permessi")
public class AdminPermessiProcessoController {

    private final RegolePermessiProcesso regole;

    public AdminPermessiProcessoController(RegolePermessiProcesso regole) {
        this.regole = regole;
    }

    @GetMapping
    public List<RegolePermessiProcesso.Regola> elenco(@PathVariable String chiave) {
        return regole.elenco(chiave);
    }

    @PutMapping
    public List<RegolePermessiProcesso.Regola> sostituisci(@PathVariable String chiave,
                                                           @RequestBody List<RegolePermessiProcesso.Regola> nuove) {
        return regole.sostituisci(chiave, nuove);
    }
}
