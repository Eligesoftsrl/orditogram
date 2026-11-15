package com.eligesoft.orditogram.webhook;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Un evento da consegnare (o già consegnato) a un webhook: è la "coda in uscita".
 * Si scrive nella stessa transazione dell'operazione che genera l'evento: se l'operazione viene annullata,
 * l'avviso non parte; se Orditogram si ferma, alla ripartenza l'avviso è ancora in coda.
 */
@Entity
@Table(name = "consegna_webhook", indexes = {
    @Index(name = "ix_consegna_coda", columnList = "stato, prossimoTentativo"),
    @Index(name = "ix_consegna_webhook", columnList = "webhookId, id")
})
public class ConsegnaWebhook {

    /** Stati (testo, non enum: aggiungerne non richiede di modificare il database). */
    public static final String IN_CODA = "IN_CODA";
    public static final String IN_INVIO = "IN_INVIO";
    public static final String CONSEGNATA = "CONSEGNATA";
    public static final String DA_RIPETERE = "DA_RIPETERE";
    public static final String FALLITA = "FALLITA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long webhookId;

    @Column(nullable = false, length = 64)
    private String eventoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(length = 64)
    private String pratica;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, length = 20)
    private String stato = IN_CODA;

    @Column(nullable = false)
    private int tentativi;

    @Column(nullable = false)
    private OffsetDateTime prossimoTentativo;

    @Column(nullable = false)
    private OffsetDateTime creata;

    private OffsetDateTime ultimoTentativo;

    private Integer codiceRisposta;

    @Column(length = 2000)
    private String risposta;

    @Column(length = 1000)
    private String errore;

    private Long durataMs;

    public Long getId() { return id; }
    public Long getWebhookId() { return webhookId; }
    public void setWebhookId(Long webhookId) { this.webhookId = webhookId; }
    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getPratica() { return pratica; }
    public void setPratica(String pratica) { this.pratica = pratica; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStato() { return stato; }
    public void setStato(String stato) { this.stato = stato; }
    public int getTentativi() { return tentativi; }
    public void setTentativi(int tentativi) { this.tentativi = tentativi; }
    public OffsetDateTime getProssimoTentativo() { return prossimoTentativo; }
    public void setProssimoTentativo(OffsetDateTime prossimoTentativo) { this.prossimoTentativo = prossimoTentativo; }
    public OffsetDateTime getCreata() { return creata; }
    public void setCreata(OffsetDateTime creata) { this.creata = creata; }
    public OffsetDateTime getUltimoTentativo() { return ultimoTentativo; }
    public void setUltimoTentativo(OffsetDateTime ultimoTentativo) { this.ultimoTentativo = ultimoTentativo; }
    public Integer getCodiceRisposta() { return codiceRisposta; }
    public void setCodiceRisposta(Integer codiceRisposta) { this.codiceRisposta = codiceRisposta; }
    public String getRisposta() { return risposta; }
    public void setRisposta(String risposta) { this.risposta = risposta; }
    public String getErrore() { return errore; }
    public void setErrore(String errore) { this.errore = errore; }
    public Long getDurataMs() { return durataMs; }
    public void setDurataMs(Long durataMs) { this.durataMs = durataMs; }
}
