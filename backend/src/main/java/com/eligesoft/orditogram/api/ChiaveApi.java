package com.eligesoft.orditogram.api;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Una chiave con cui un sistema esterno (portale, gestionale, app) usa l'API pubblica di Orditogram.
 * Della chiave si conserva solo l'impronta SHA-256: il valore si vede una volta sola, alla creazione.
 */
@Entity
@Table(name = "chiave_api", indexes = @Index(name = "ix_chiave_api_prefisso", columnList = "prefisso"))
public class ChiaveApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome del sistema, es. "portale-cittadino": compare nello storico delle pratiche. */
    @Column(nullable = false, unique = true, length = 60)
    private String nome;

    private String descrizione;

    /** Primi caratteri della chiave, per riconoscerla nell'elenco e trovarla velocemente. */
    @Column(nullable = false, length = 20)
    private String prefisso;

    @Column(nullable = false, length = 64)
    private String impronta;

    /** Permessi separati da virgola, es. "pratiche:avvia,pratiche:leggi". */
    @Column(nullable = false, length = 500)
    private String ambiti;

    /** Chiavi dei processi consentiti separate da virgola; vuoto = tutti. */
    @Column(length = 2000)
    private String processi;

    /** Codici dei tipi di anagrafica consentiti, separati da virgola; vuoto = nessuno (mai tutti per difetto). */
    @Column(length = 2000)
    private String anagrafiche;

    private LocalDate scadenza;

    @Column(nullable = false)
    private boolean attiva = true;

    @Column(nullable = false)
    private OffsetDateTime creata;

    @Column(length = 100)
    private String creataDa;

    private OffsetDateTime ultimoUso;

    @Column(length = 64)
    private String ultimoIndirizzo;

    private OffsetDateTime revocata;

    public Set<String> ambitiSet() { return elenco(ambiti); }
    public Set<String> processiSet() { return elenco(processi); }
    public Set<String> anagraficheSet() { return elenco(anagrafiche); }

    static Set<String> elenco(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String x : s.split(",")) if (!x.isBlank()) out.add(x.trim());
        return out;
    }

    public boolean valida(LocalDate oggi) {
        return attiva && revocata == null && (scadenza == null || !oggi.isAfter(scadenza));
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    public String getPrefisso() { return prefisso; }
    public void setPrefisso(String prefisso) { this.prefisso = prefisso; }
    public String getImpronta() { return impronta; }
    public void setImpronta(String impronta) { this.impronta = impronta; }
    public String getAmbiti() { return ambiti; }
    public void setAmbiti(String ambiti) { this.ambiti = ambiti; }
    public String getProcessi() { return processi; }
    public void setProcessi(String processi) { this.processi = processi; }
    public String getAnagrafiche() { return anagrafiche; }
    public void setAnagrafiche(String anagrafiche) { this.anagrafiche = anagrafiche; }
    public LocalDate getScadenza() { return scadenza; }
    public void setScadenza(LocalDate scadenza) { this.scadenza = scadenza; }
    public boolean isAttiva() { return attiva; }
    public void setAttiva(boolean attiva) { this.attiva = attiva; }
    public OffsetDateTime getCreata() { return creata; }
    public void setCreata(OffsetDateTime creata) { this.creata = creata; }
    public String getCreataDa() { return creataDa; }
    public void setCreataDa(String creataDa) { this.creataDa = creataDa; }
    public OffsetDateTime getUltimoUso() { return ultimoUso; }
    public void setUltimoUso(OffsetDateTime ultimoUso) { this.ultimoUso = ultimoUso; }
    public String getUltimoIndirizzo() { return ultimoIndirizzo; }
    public void setUltimoIndirizzo(String ultimoIndirizzo) { this.ultimoIndirizzo = ultimoIndirizzo; }
    public OffsetDateTime getRevocata() { return revocata; }
    public void setRevocata(OffsetDateTime revocata) { this.revocata = revocata; }
}
