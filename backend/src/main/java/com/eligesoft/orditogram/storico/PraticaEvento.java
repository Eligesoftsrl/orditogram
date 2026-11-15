package com.eligesoft.orditogram.storico;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Un evento nella vita di una pratica: avvio, presa in carico, completamento di un task,
 * caricamento di un documento, notifica. È il "registro" che mostra chi ha fatto cosa e quando.
 */
@Entity
@Table(name = "pratica_evento", indexes = {
    @Index(name = "ix_evento_pratica", columnList = "processInstanceId"),
    @Index(name = "ix_evento_utente", columnList = "utente")
})
public class PraticaEvento {

    public enum Tipo { AVVIO, PRESA_IN_CARICO, RILASCIO, COMPLETAMENTO, DOCUMENTO, NOTIFICA, SCADENZA, EMAIL, CHIAMATA_API, ERRORE, MESSAGGIO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String processInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Tipo tipo;

    /** Es. "Verifica pratica", "Nuova versione di contratto.pdf". */
    private String titolo;

    /** Username di chi ha compiuto l'azione. */
    @Column(length = 100)
    private String utente;

    /** Nome completo al momento dell'azione. */
    private String utenteNome;

    @Column(nullable = false)
    private OffsetDateTime quando;

    /** JSON: [{ "label": "Esito", "value": "Da revisionare" }, ...] */
    @Column(columnDefinition = "text")
    private String dettagli;

    public Long getId() { return id; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public String getTitolo() { return titolo; }
    public void setTitolo(String titolo) { this.titolo = titolo; }
    public String getUtente() { return utente; }
    public void setUtente(String utente) { this.utente = utente; }
    public String getUtenteNome() { return utenteNome; }
    public void setUtenteNome(String utenteNome) { this.utenteNome = utenteNome; }
    public OffsetDateTime getQuando() { return quando; }
    public void setQuando(OffsetDateTime quando) { this.quando = quando; }
    public String getDettagli() { return dettagli; }
    public void setDettagli(String dettagli) { this.dettagli = dettagli; }
}
