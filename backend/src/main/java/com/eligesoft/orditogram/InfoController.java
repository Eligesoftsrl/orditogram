package com.eligesoft.orditogram;

import com.eligesoft.orditogram.glossario.GlossarioService;
import com.eligesoft.orditogram.personalizzazione.PersonalizzazioneService;
import com.eligesoft.orditogram.modulo.ModuliService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Nome, logo e versione di Orditogram, glossario e moduli accesi dell'installazione (pubblici: li usa anche la pagina di accesso). */
@RestController
public class InfoController {

    private final ObjectProvider<BuildProperties> build;
    private final GlossarioService glossario;
    private final PersonalizzazioneService personalizzazione;
    private final ModuliService moduli;

    public InfoController(ObjectProvider<BuildProperties> build, GlossarioService glossario, ModuliService moduli,
                          PersonalizzazioneService personalizzazione) {
        this.build = build;
        this.glossario = glossario;
        this.moduli = moduli;
        this.personalizzazione = personalizzazione;
    }

    @GetMapping("/api/info")
    public Map<String, Object> info() {
        BuildProperties b = build.getIfAvailable();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> aspetto = personalizzazione.stato();
        m.put("nome", aspetto.get("nome"));
        m.put("logo", aspetto.get("logo"));
        m.put("versione", b != null ? b.getVersion() : "sviluppo");
        m.put("build", b != null && b.getTime() != null ? b.getTime().toString() : null);
        m.put("glossario", glossario.parole());
        m.put("moduli", moduli.accesi());
        return m;
    }

    /** Logo del cliente (pubblico: serve anche alla pagina di accesso). L'indirizzo cambia a ogni logo nuovo. */
    @GetMapping("/api/info/logo")
    public ResponseEntity<byte[]> logo() {
        return personalizzazione.logo()
            .map(l -> ResponseEntity.ok().contentType(MediaType.parseMediaType(l.tipo()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .header("Content-Security-Policy", "default-src 'none'")
                .body(l.contenuto()))
            .orElse(ResponseEntity.notFound().build());
    }
}
