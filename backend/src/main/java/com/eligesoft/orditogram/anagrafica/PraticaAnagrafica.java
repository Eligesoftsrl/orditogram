package com.eligesoft.orditogram.anagrafica;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Una scheda collegata a una pratica dal campo "anagrafica" di un form, con la fotografia dei dati di quel momento
 * (senza i campi riservati) e il numero della versione fotografata. Un campo collega una sola scheda per pratica:
 * se un form successivo lo cambia, il collegamento si aggiorna e lo storico della pratica conserva il passaggio.
 */
@Entity
@Table(name = "pratica_anagrafica")
public class PraticaAnagrafica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    @Column(nullable = false, length = 100)
    private String campo;

    @Column(name = "scheda_id", nullable = false)
    private Long schedaId;

    @Column(nullable = false)
    private long versioneScheda;

    @Column(nullable = false, length = 500)
    private String titolo;

    @Column(nullable = false, columnDefinition = "text")
    private String fotografia;

    @Column(nullable = false)
    private OffsetDateTime collegataIl;

    private String collegataDa;

    public Long getId() { return id; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getCampo() { return campo; }
    public void setCampo(String campo) { this.campo = campo; }
    public Long getSchedaId() { return schedaId; }
    public void setSchedaId(Long schedaId) { this.schedaId = schedaId; }
    public long getVersioneScheda() { return versioneScheda; }
    public void setVersioneScheda(long versioneScheda) { this.versioneScheda = versioneScheda; }
    public String getTitolo() { return titolo; }
    public void setTitolo(String titolo) { this.titolo = titolo; }
    public String getFotografia() { return fotografia; }
    public void setFotografia(String fotografia) { this.fotografia = fotografia; }
    public OffsetDateTime getCollegataIl() { return collegataIl; }
    public void setCollegataIl(OffsetDateTime collegataIl) { this.collegataIl = collegataIl; }
    public String getCollegataDa() { return collegataDa; }
    public void setCollegataDa(String collegataDa) { this.collegataDa = collegataDa; }
}
