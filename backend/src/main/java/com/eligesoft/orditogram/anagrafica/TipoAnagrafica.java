package com.eligesoft.orditogram.anagrafica;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Un tipo di anagrafica definito dall'analista (es. "Fondi", "Fornitori", "Immobili"): codice stabile, nome e la
 * definizione dei campi nello stesso formato JSON dei form (vedi DefinizioneAnagrafica). La versione cresce a ogni
 * modifica della definizione e resta scritta nelle versioni delle schede.
 */
@Entity
@Table(name = "anagrafica_tipo")
public class TipoAnagrafica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String codice;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 1000)
    private String descrizione;

    @Column(nullable = false, columnDefinition = "text")
    private String definizione;

    @Column(nullable = false)
    private int versione;

    @Column(nullable = false)
    private boolean attivo = true;

    @Column(nullable = false)
    private OffsetDateTime creatoIl;
    private String creatoDa;
    @Column(nullable = false)
    private OffsetDateTime modificatoIl;
    private String modificatoDa;

    public Long getId() { return id; }
    public String getCodice() { return codice; }
    public void setCodice(String codice) { this.codice = codice; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    public String getDefinizione() { return definizione; }
    public void setDefinizione(String definizione) { this.definizione = definizione; }
    public int getVersione() { return versione; }
    public void setVersione(int versione) { this.versione = versione; }
    public boolean isAttivo() { return attivo; }
    public void setAttivo(boolean attivo) { this.attivo = attivo; }
    public OffsetDateTime getCreatoIl() { return creatoIl; }
    public void setCreatoIl(OffsetDateTime creatoIl) { this.creatoIl = creatoIl; }
    public String getCreatoDa() { return creatoDa; }
    public void setCreatoDa(String creatoDa) { this.creatoDa = creatoDa; }
    public OffsetDateTime getModificatoIl() { return modificatoIl; }
    public void setModificatoIl(OffsetDateTime modificatoIl) { this.modificatoIl = modificatoIl; }
    public String getModificatoDa() { return modificatoDa; }
    public void setModificatoDa(String modificatoDa) { this.modificatoDa = modificatoDa; }
}
