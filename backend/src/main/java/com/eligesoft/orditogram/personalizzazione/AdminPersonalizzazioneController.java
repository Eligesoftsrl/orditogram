package com.eligesoft.orditogram.personalizzazione;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Studio → Personalizzazione: nome e logo. Solo per l'amministratore (regola generale di /api/admin/**). */
@RestController
@RequestMapping("/api/admin/personalizzazione")
public class AdminPersonalizzazioneController {

    private final PersonalizzazioneService servizio;

    public AdminPersonalizzazioneController(PersonalizzazioneService servizio) {
        this.servizio = servizio;
    }

    @GetMapping
    public Map<String, Object> stato() {
        return servizio.stato();
    }

    @PutMapping("/nome")
    public Map<String, Object> nome(@RequestBody Map<String, String> corpo) {
        return servizio.salvaNome(corpo != null ? corpo.get("nome") : null);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> logo(@RequestParam("file") MultipartFile file) throws IOException {
        return servizio.salvaLogo(file.getBytes());
    }

    @DeleteMapping("/logo")
    public Map<String, Object> togliLogo() {
        return servizio.togliLogo();
    }
}
