package com.eligesoft.orditogram.registro;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Un'azione di amministrazione o di sicurezza: chi, cosa, su quale oggetto, quando, da dove. */
@Entity
@Table(name = "registro_azione", indexes = {
    @Index(name = "ix_registro_quando", columnList = "quando"),
    @Index(name = "ix_registro_utente", columnList = "utente"),
    @Index(name = "ix_registro_azione", columnList = "azione")
})
public class RegistroAzione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime quando;

    @Column(length = 100)
    private String utente;

    private String utenteNome;

    /** Es. PROCESSO_PUBBLICATO, FORM_SALVATO, UTENTE_MODIFICATO, ACCESSO_NEGATO. */
    @Column(nullable = false, length = 40)
    private String azione;

    /** Es. "accessoAtti v3", "aa-esame", "mario". */
    private String oggetto;

    @Column(columnDefinition = "text")
    private String dettagli;

    @Column(length = 64)
    private String indirizzo;

    public Long getId() { return id; }
    public OffsetDateTime getQuando() { return quando; }
    public void setQuando(OffsetDateTime quando) { this.quando = quando; }
    public String getUtente() { return utente; }
    public void setUtente(String utente) { this.utente = utente; }
    public String getUtenteNome() { return utenteNome; }
    public void setUtenteNome(String utenteNome) { this.utenteNome = utenteNome; }
    public String getAzione() { return azione; }
    public void setAzione(String azione) { this.azione = azione; }
    public String getOggetto() { return oggetto; }
    public void setOggetto(String oggetto) { this.oggetto = oggetto; }
    public String getDettagli() { return dettagli; }
    public void setDettagli(String dettagli) { this.dettagli = dettagli; }
    public String getIndirizzo() { return indirizzo; }
    public void setIndirizzo(String indirizzo) { this.indirizzo = indirizzo; }
}
