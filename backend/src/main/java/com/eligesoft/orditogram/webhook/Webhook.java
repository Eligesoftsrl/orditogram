package com.eligesoft.orditogram.webhook;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Un indirizzo esterno che Orditogram avvisa quando succede qualcosa (eventi in webhook/TipiEvento). */
@Entity
@Table(name = "webhook")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nome;

    @Column(nullable = false, length = 1000)
    private String url;

    private String descrizione;

    /** Tipi di evento separati da virgola; vuoto = tutti. */
    @Column(length = 1000)
    private String eventi;

    /** Chiavi dei processi separate da virgola; vuoto = tutti. */
    @Column(length = 2000)
    private String processi;

    /** Segreto per la firma HMAC, cifrato se è impostata app.sicurezza.chiave-cifratura. */
    @Column(nullable = false, length = 500)
    private String segreto;

    @Column(nullable = false)
    private boolean attivo = true;

    @Column(nullable = false)
    private OffsetDateTime creato;

    @Column(length = 100)
    private String creatoDa;

    public Set<String> eventiSet() { return elenco(eventi); }
    public Set<String> processiSet() { return elenco(processi); }

    static Set<String> elenco(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String x : s.split(",")) if (!x.isBlank()) out.add(x.trim());
        return out;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    public String getEventi() { return eventi; }
    public void setEventi(String eventi) { this.eventi = eventi; }
    public String getProcessi() { return processi; }
    public void setProcessi(String processi) { this.processi = processi; }
    public String getSegreto() { return segreto; }
    public void setSegreto(String segreto) { this.segreto = segreto; }
    public boolean isAttivo() { return attivo; }
    public void setAttivo(boolean attivo) { this.attivo = attivo; }
    public OffsetDateTime getCreato() { return creato; }
    public void setCreato(OffsetDateTime creato) { this.creato = creato; }
    public String getCreatoDa() { return creatoDa; }
    public void setCreatoDa(String creatoDa) { this.creatoDa = creatoDa; }
}
