package com.eligesoft.orditogram.registro;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Un errore imprevisto: una richiesta finita con un errore interno o un lavoro del motore fallito (timer,
 * connettore). Il codice è quello che la persona vede nel messaggio e che riporta all'assistenza.
 */
@Entity
@Table(name = "registro_errore")
public class RegistroErrore {

    public static final String RICHIESTA = "RICHIESTA";
    public static final String MOTORE = "MOTORE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime quando;

    @Column(nullable = false, length = 20, unique = true)
    private String codice;

    @Column(nullable = false, length = 20)
    private String origine;

    @Column(length = 100)
    private String utente;

    @Column(length = 500)
    private String richiesta;

    private String tipo;

    @Column(columnDefinition = "text")
    private String messaggio;

    @Column(columnDefinition = "text")
    private String traccia;

    @Column(length = 64)
    private String indirizzo;

    protected RegistroErrore() {}

    public RegistroErrore(OffsetDateTime quando, String codice, String origine, String utente, String richiesta,
                          String tipo, String messaggio, String traccia, String indirizzo) {
        this.quando = quando;
        this.codice = codice;
        this.origine = origine;
        this.utente = utente;
        this.richiesta = richiesta;
        this.tipo = tipo;
        this.messaggio = messaggio;
        this.traccia = traccia;
        this.indirizzo = indirizzo;
    }

    public Long getId() { return id; }
    public OffsetDateTime getQuando() { return quando; }
    public String getCodice() { return codice; }
    public String getOrigine() { return origine; }
    public String getUtente() { return utente; }
    public String getRichiesta() { return richiesta; }
    public String getTipo() { return tipo; }
    public String getMessaggio() { return messaggio; }
    public String getTraccia() { return traccia; }
    public String getIndirizzo() { return indirizzo; }
}
