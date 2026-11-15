package com.eligesoft.orditogram.integrazione;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Nomi delle credenziali configurate, per la scelta nell'editor. I segreti restano sul server. */
@RestController
@RequestMapping("/api/admin/credenziali")
public class CredenzialiController {

    private final Credenziali credenziali;

    public CredenzialiController(Credenziali credenziali) {
        this.credenziali = credenziali;
    }

    @GetMapping
    public List<Map<String, String>> elenco() {
        return credenziali.elenco();
    }
}
