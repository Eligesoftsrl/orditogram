package com.eligesoft.orditogram.anagrafica;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Una scheda di un'anagrafica (es. un fondo, un fornitore): i dati in JSON, validati con la definizione del tipo.
 * Non si cancella: si cessa e si può riattivare. La versione serve al blocco ottimistico (due persone che
 * modificano la stessa scheda) ed è anche il numero dell'ultima versione salvata in anagrafica_versione.
 */
@Entity
@Table(name = "anagrafica_scheda")
public class SchedaAnagrafica {

    public static final String ATTIVA = "ATTIVA";
    public static final String CESSATA = "CESSATA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoAnagrafica tipo;

    @Column(nullable = false, length = 500)
    private String titolo;

    @Column(nullable = false, columnDefinition = "text")
    private String dati;

    @Column(nullable = false, length = 20)
    private String stato = ATTIVA;

    @Version
    @Column(nullable = false)
    private long versione;

    @Column(nullable = false)
    private OffsetDateTime creataIl;
    private String creataDa;
    @Column(nullable = false)
    private OffsetDateTime modificataIl;
    private String modificataDa;

    public Long getId() { return id; }
    public TipoAnagrafica getTipo() { return tipo; }
    public void setTipo(TipoAnagrafica tipo) { this.tipo = tipo; }
    public String getTitolo() { return titolo; }
    public void setTitolo(String titolo) { this.titolo = titolo; }
    public String getDati() { return dati; }
    public void setDati(String dati) { this.dati = dati; }
    public String getStato() { return stato; }
    public void setStato(String stato) { this.stato = stato; }
    public boolean isAttiva() { return ATTIVA.equals(stato); }
    public long getVersione() { return versione; }
    public OffsetDateTime getCreataIl() { return creataIl; }
    public void setCreataIl(OffsetDateTime creataIl) { this.creataIl = creataIl; }
    public String getCreataDa() { return creataDa; }
    public void setCreataDa(String creataDa) { this.creataDa = creataDa; }
    public OffsetDateTime getModificataIl() { return modificataIl; }
    public void setModificataIl(OffsetDateTime modificataIl) { this.modificataIl = modificataIl; }
    public String getModificataDa() { return modificataDa; }
    public void setModificataDa(String modificataDa) { this.modificataDa = modificataDa; }
}
