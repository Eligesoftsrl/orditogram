package com.eligesoft.orditogram.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * app:
 *   api:
 *     richieste-al-minuto: 600        # per sistema; 0 = nessun limite
 *     cors-origini: [https://portale.ente.it]   # siti che chiamano l'API direttamente dal browser
 */
@Component
@ConfigurationProperties(prefix = "app.api")
public class ApiProperties {

    private int richiesteAlMinuto = 600;
    private List<String> corsOrigini = new ArrayList<>();

    public int getRichiesteAlMinuto() { return richiesteAlMinuto; }
    public void setRichiesteAlMinuto(int richiesteAlMinuto) { this.richiesteAlMinuto = richiesteAlMinuto; }
    public List<String> getCorsOrigini() { return corsOrigini; }
    public void setCorsOrigini(List<String> corsOrigini) { this.corsOrigini = corsOrigini; }
}
