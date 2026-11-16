package com.eligesoft.orditogram.anagrafica;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Una versione di una scheda: i dati completi dopo l'operazione, chi l'ha fatta, quando e con quale nota. */
@Entity
@Table(name = "anagrafica_versione")
public class VersioneScheda {

    public static final String CREATA = "CREATA";
    public static final String MODIFICATA = "MODIFICATA";
    public static final String CESSATA = "CESSATA";
    public static final String RIATTIVATA = "RIATTIVATA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheda_id", nullable = false)
    private Long schedaId;

    @Column(nullable = false)
    private long numero;

    @Column(nullable = false, length = 20)
    private String operazione;

    @Column(nullable = false, columnDefinition = "text")
    private String dati;

    @Column(nullable = false, length = 20)
    private String stato;

    @Column(nullable = false)
    private int versioneTipo;

    @Column(nullable = false)
    private OffsetDateTime il;

    private String utente;

    @Column(length = 1000)
    private String nota;

    public VersioneScheda() { }

    public VersioneScheda(SchedaAnagrafica s, long numero, String operazione, String utente, String nota) {
        this.schedaId = s.getId();
        this.numero = numero;
        this.operazione = operazione;
        this.dati = s.getDati();
        this.stato = s.getStato();
        this.versioneTipo = s.getTipo().getVersione();
        this.il = OffsetDateTime.now();
        this.utente = utente;
        this.nota = nota;
    }

    public Long getId() { return id; }
    public Long getSchedaId() { return schedaId; }
    public long getNumero() { return numero; }
    public String getOperazione() { return operazione; }
    public String getDati() { return dati; }
    public String getStato() { return stato; }
    public int getVersioneTipo() { return versioneTipo; }
    public OffsetDateTime getIl() { return il; }
    public String getUtente() { return utente; }
    public String getNota() { return nota; }
}
