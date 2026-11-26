package com.eligesoft.orditogram.registro;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Una lettura di dati: chi ha aperto quale pratica, attività, documento o scheda, quando e da dove. */
@Entity
@Table(name = "registro_lettura")
public class RegistroLettura {

    public static final String CONSENTITA = "CONSENTITA";
    public static final String NEGATA = "NEGATA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime quando;

    @Column(length = 100)
    private String utente;

    @Column(nullable = false, length = 40)
    private String azione;

    private String oggetto;

    @Column(length = 500)
    private String dettagli;

    @Column(nullable = false, length = 20)
    private String esito;

    @Column(length = 64)
    private String indirizzo;

    protected RegistroLettura() {}

    public RegistroLettura(OffsetDateTime quando, String utente, String azione, String oggetto, String dettagli,
                           String esito, String indirizzo) {
        this.quando = quando;
        this.utente = utente;
        this.azione = azione;
        this.oggetto = oggetto;
        this.dettagli = dettagli;
        this.esito = esito;
        this.indirizzo = indirizzo;
    }

    public Long getId() { return id; }
    public OffsetDateTime getQuando() { return quando; }
    public String getUtente() { return utente; }
    public String getAzione() { return azione; }
    public String getOggetto() { return oggetto; }
    public String getDettagli() { return dettagli; }
    public String getEsito() { return esito; }
    public String getIndirizzo() { return indirizzo; }
}
