package com.eligesoft.orditogram.accesso;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Una parte della pratica: chi la pratica riguarda dall'esterno, con il suo ruolo ("richiedente", "controparte",
 * "istituto"...). È una persona (utente esterno) o un'organizzazione esterna, i cui utenti vedono la pratica
 * e lavorano le attività assegnate a quella parte (candidateGroups="parte:controparte").
 */
@Entity
@Table(name = "pratica_parte")
public class PraticaParte {

    /** Ruolo di chi avvia la pratica da utente esterno. */
    public static final String RICHIEDENTE = "richiedente";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String processInstanceId;

    @Column(nullable = false, length = 60)
    private String ruolo;

    /** Username della persona, se la parte è una persona. */
    @Column(length = 100)
    private String utente;

    /** Organizzazione esterna della parte (anche per una persona: quella a cui appartiene, se una sola). */
    private Long organizzazioneId;

    @Column(nullable = false)
    private OffsetDateTime aggiuntaIl;

    @Column(length = 100)
    private String aggiuntaDa;

    public Long getId() { return id; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getRuolo() { return ruolo; }
    public void setRuolo(String ruolo) { this.ruolo = ruolo; }
    public String getUtente() { return utente; }
    public void setUtente(String utente) { this.utente = utente; }
    public Long getOrganizzazioneId() { return organizzazioneId; }
    public void setOrganizzazioneId(Long organizzazioneId) { this.organizzazioneId = organizzazioneId; }
    public OffsetDateTime getAggiuntaIl() { return aggiuntaIl; }
    public void setAggiuntaIl(OffsetDateTime aggiuntaIl) { this.aggiuntaIl = aggiuntaIl; }
    public String getAggiuntaDa() { return aggiuntaDa; }
    public void setAggiuntaDa(String aggiuntaDa) { this.aggiuntaDa = aggiuntaDa; }
}
