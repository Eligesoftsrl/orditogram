package com.eligesoft.orditogram.bozza;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Bozza di un processo in lavorazione nell'editor: non è in produzione finché non viene pubblicata.
 * Una sola bozza per processo (chiave = id del processo nel BPMN).
 */
@Entity
@Table(name = "processo_bozza")
public class ProcessoBozza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String chiave;

    private String nome;

    @Column(nullable = false, columnDefinition = "text")
    private String xml;

    private String aggiornatoDa;
    private OffsetDateTime aggiornatoIl;

    public Long getId() { return id; }
    public String getChiave() { return chiave; }
    public void setChiave(String chiave) { this.chiave = chiave; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getXml() { return xml; }
    public void setXml(String xml) { this.xml = xml; }
    public String getAggiornatoDa() { return aggiornatoDa; }
    public void setAggiornatoDa(String aggiornatoDa) { this.aggiornatoDa = aggiornatoDa; }
    public OffsetDateTime getAggiornatoIl() { return aggiornatoIl; }
    public void setAggiornatoIl(OffsetDateTime aggiornatoIl) { this.aggiornatoIl = aggiornatoIl; }
}
