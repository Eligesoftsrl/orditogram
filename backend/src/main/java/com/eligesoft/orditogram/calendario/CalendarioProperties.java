package com.eligesoft.orditogram.calendario;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * app:
 *   calendario:
 *     giorni-non-lavorativi: [SATURDAY, SUNDAY]   # anche in italiano: sabato, domenica
 *     festivita-nazionali: true
 *     zona: Europe/Rome
 *     festivita:
 *       - "06-29 Santi Pietro e Paolo (patrono)"  # ogni anno
 *       - "2026-12-07 Chiusura uffici"            # una volta sola
 */
@Component
@ConfigurationProperties(prefix = "app.calendario")
public class CalendarioProperties {

    private List<String> giorniNonLavorativi = new ArrayList<>(List.of("SATURDAY", "SUNDAY"));
    private boolean festivitaNazionali = true;
    private List<String> festivita = new ArrayList<>();
    /** Fuso orario in cui si contano i giorni (il server può essere in UTC). */
    private String zona = "Europe/Rome";

    public List<String> getGiorniNonLavorativi() { return giorniNonLavorativi; }
    public void setGiorniNonLavorativi(List<String> v) { this.giorniNonLavorativi = v; }
    public boolean isFestivitaNazionali() { return festivitaNazionali; }
    public void setFestivitaNazionali(boolean v) { this.festivitaNazionali = v; }
    public List<String> getFestivita() { return festivita; }
    public void setFestivita(List<String> v) { this.festivita = v; }

    public String getZona() { return zona; }
    public void setZona(String v) { this.zona = v; }
    public java.time.ZoneId zoneId() { return java.time.ZoneId.of(zona); }

    /** Controllo all'avvio: una festività scritta male si scopre subito, non quando scatta un timer. */
    @jakarta.annotation.PostConstruct
    void controlla() {
        calendario();
        zoneId();
    }

    public CalendarioLavorativo calendario() {
        return CalendarioLavorativo.da(giorniNonLavorativi, festivitaNazionali, festivita);
    }
}
