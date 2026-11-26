package com.eligesoft.orditogram;

import com.eligesoft.orditogram.glossario.GlossarioService;
import com.eligesoft.orditogram.modulo.ModuliService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Versione di Orditogram, glossario e moduli accesi dell'installazione (pubblici: li usa anche la pagina di accesso). */
@RestController
public class InfoController {

    private final ObjectProvider<BuildProperties> build;
    private final GlossarioService glossario;
    private final ModuliService moduli;

    public InfoController(ObjectProvider<BuildProperties> build, GlossarioService glossario, ModuliService moduli) {
        this.build = build;
        this.glossario = glossario;
        this.moduli = moduli;
    }

    @GetMapping("/api/info")
    public Map<String, Object> info() {
        BuildProperties b = build.getIfAvailable();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", "Orditogram");
        m.put("versione", b != null ? b.getVersion() : "sviluppo");
        m.put("build", b != null && b.getTime() != null ? b.getTime().toString() : null);
        m.put("glossario", glossario.parole());
        m.put("moduli", moduli.accesi());
        return m;
    }
}
