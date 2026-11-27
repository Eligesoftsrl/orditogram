package com.eligesoft.orditogram.personalizzazione;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Nome e logo scelti dall'installazione: una sola riga (id 1). Il logo è salvato in base64 con il suo tipo. */
@Entity
@Table(name = "personalizzazione")
public class Personalizzazione {

    static final int RIGA = 1;

    @Id
    private Integer id = RIGA;

    @Column(length = 60)
    private String nome;

    @Column(name = "logo_tipo", length = 40)
    private String logoTipo;

    @Column(columnDefinition = "text")
    private String logo;

    @Column(name = "aggiornata_il", nullable = false)
    private OffsetDateTime aggiornataIl = OffsetDateTime.now();

    @Column(name = "aggiornata_da", length = 100)
    private String aggiornataDa;

    public Integer getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getLogoTipo() { return logoTipo; }
    public void setLogoTipo(String logoTipo) { this.logoTipo = logoTipo; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public OffsetDateTime getAggiornataIl() { return aggiornataIl; }
    public void setAggiornataIl(OffsetDateTime aggiornataIl) { this.aggiornataIl = aggiornataIl; }
    public String getAggiornataDa() { return aggiornataDa; }
    public void setAggiornataDa(String aggiornataDa) { this.aggiornataDa = aggiornataDa; }
}
