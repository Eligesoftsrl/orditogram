package com.eligesoft.orditogram.documento;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Una versione di un documento: il file vero, chi l'ha caricato, in quale fase, e l'esito della verifica firma. */
@Entity
@Table(name = "documento_versione")
public class DocumentoVersione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    private int numero;

    @Column(nullable = false)
    private String filename;

    private String contentType;
    private long dimensione;

    @Column(length = 64)
    private String sha256;

    /** Percorso relativo nella cartella documenti (app.documenti.dir). */
    @Column(nullable = false)
    private String percorso;

    private String caricatoDa;
    private String caricatoDaNome;
    private OffsetDateTime caricatoIl;

    /** Fase del processo in cui è stato caricato (es. "Avvio", "Revisione pratica"). */
    private String fase;

    @Column(columnDefinition = "text")
    private String nota;

    /** NESSUNA, PADES, CADES, NON_LEGGIBILE */
    @Column(length = 20)
    private String firmaTipo;

    /** OK, KO, ATTENZIONE, NESSUNA */
    @Column(length = 20)
    private String firmaEsito;

    /** Report completo della verifica (JSON). */
    @Column(columnDefinition = "text")
    private String firmaReport;

    public Long getId() { return id; }
    public Documento getDocumento() { return documento; }
    public void setDocumento(Documento documento) { this.documento = documento; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getDimensione() { return dimensione; }
    public void setDimensione(long dimensione) { this.dimensione = dimensione; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getPercorso() { return percorso; }
    public void setPercorso(String percorso) { this.percorso = percorso; }
    public String getCaricatoDa() { return caricatoDa; }
    public void setCaricatoDa(String caricatoDa) { this.caricatoDa = caricatoDa; }
    public String getCaricatoDaNome() { return caricatoDaNome; }
    public void setCaricatoDaNome(String caricatoDaNome) { this.caricatoDaNome = caricatoDaNome; }
    public OffsetDateTime getCaricatoIl() { return caricatoIl; }
    public void setCaricatoIl(OffsetDateTime caricatoIl) { this.caricatoIl = caricatoIl; }
    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
    public String getFirmaTipo() { return firmaTipo; }
    public void setFirmaTipo(String firmaTipo) { this.firmaTipo = firmaTipo; }
    public String getFirmaEsito() { return firmaEsito; }
    public void setFirmaEsito(String firmaEsito) { this.firmaEsito = firmaEsito; }
    public String getFirmaReport() { return firmaReport; }
    public void setFirmaReport(String firmaReport) { this.firmaReport = firmaReport; }
}
