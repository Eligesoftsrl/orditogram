package com.eligesoft.orditogram.integrazione;

import com.eligesoft.orditogram.webhook.RicevitoreProva;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servizi esterni "finti" per provare il connettore API senza un sistema reale.
 * Si disattivano con app.demo.servizi=false.
 *
 * POST /api/demo/protocollo  (intestazione X-API-Key come da credenziale "protocollo-demo")
 *   corpo:    {"oggetto": "...", "mittente": "...", "verso": "entrata"}
 *   risposta: {"numero": "2026/000123", "data": "2026-10-04", "registro": "Protocollo generale", ...}
 *   con ?simula=errore risponde 503, utile per provare la strada di errore.
 */
@RestController
@RequestMapping("/api/demo")
public class ServiziDemoController {

    private final AtomicInteger contatore = new AtomicInteger(100);
    private final boolean attivi;
    private final Credenziali credenziali;
    private final RicevitoreProva ricevitore;

    private final com.eligesoft.orditogram.modulo.ModuliService moduli;

    public ServiziDemoController(@Value("${app.demo.servizi:true}") boolean attivi, Credenziali credenziali,
                                 RicevitoreProva ricevitore, com.eligesoft.orditogram.modulo.ModuliService moduli) {
        this.moduli = moduli;
        this.attivi = attivi;
        this.credenziali = credenziali;
        this.ricevitore = ricevitore;
    }

    /**
     * Ricevitore di prova per i webhook: accetta l'avviso, verifica la firma e lo tiene in memoria.
     * Gli avvisi ricevuti si vedono in Studio → Integrazioni (solo amministratori).
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody(required = false) String corpo,
                                                       @RequestHeader(value = "X-Orditogram-Evento", required = false) String evento,
                                                       @RequestHeader(value = "X-Orditogram-Consegna", required = false) String consegna,
                                                       @RequestHeader(value = "X-Orditogram-Firma", required = false) String firma,
                                                       @RequestParam(required = false) String simula) {
        if (!attivi) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        if ("errore".equals(simula))
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("errore", "Ricevitore di prova non disponibile (simulato)"));
        RicevitoreProva.Ricevuto r = ricevitore.ricevi(evento, consegna, firma, corpo);
        return ResponseEntity.ok(Map.of("ricevuto", true, "firma", r.esitoFirma()));
    }

    @PostMapping("/protocollo")
    public ResponseEntity<Map<String, Object>> protocolla(@RequestBody(required = false) Map<String, Object> corpo,
                                                          @RequestHeader(value = "X-API-Key", required = false) String chiave,
                                                          @RequestParam(required = false) String simula) {
        // il protocollo di prova fa parte del pacchetto della PA
        if (!attivi || !moduli.acceso(com.eligesoft.orditogram.modulo.Modulo.PA)) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        String attesa = credenziali.trova("protocollo-demo").map(Credenziali.Credenziale::getValore).orElse(null);
        if (attesa != null && !attesa.equals(chiave))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("errore", "Chiave API non valida"));
        if ("errore".equals(simula))
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("errore", "Protocollo temporaneamente non disponibile"));

        int n = contatore.incrementAndGet();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("numero", LocalDate.now().getYear() + "/" + String.format("%06d", n));
        r.put("data", LocalDate.now().toString());
        r.put("registro", "Protocollo generale");
        r.put("registratoIl", OffsetDateTime.now().toString());
        r.put("oggetto", corpo != null ? corpo.getOrDefault("oggetto", "") : "");
        return ResponseEntity.ok(r);
    }
}
