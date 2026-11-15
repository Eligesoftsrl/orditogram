package com.eligesoft.orditogram;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Versione di Orditogram (pubblica: la mostra anche la pagina di accesso). */
@RestController
public class InfoController {

    private final ObjectProvider<BuildProperties> build;

    public InfoController(ObjectProvider<BuildProperties> build) {
        this.build = build;
    }

    @GetMapping("/api/info")
    public Map<String, Object> info() {
        BuildProperties b = build.getIfAvailable();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", "Orditogram");
        m.put("versione", b != null ? b.getVersion() : "sviluppo");
        m.put("build", b != null && b.getTime() != null ? b.getTime().toString() : null);
        return m;
    }
}
