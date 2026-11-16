package com.eligesoft.orditogram.accesso;

import jakarta.persistence.*;

/**
 * Struttura dell'ente (dipartimento, ufficio, filiale) o organizzazione esterna, in un albero.
 * Le strutture danno l'ambito ai permessi per processo: "le pratiche della mia struttura" (e di quelle sotto).
 */
@Entity
@Table(name = "organizzazione")
public class Organizzazione {

    public static final String STRUTTURA = "STRUTTURA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificativo stabile (es. "prap-lombardia"), per importazioni e integrazioni. */
    @Column(nullable = false, unique = true, length = 60)
    private String codice;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 20)
    private String codiceFiscale;

    /** STRUTTURA (dell'ente); altri tipi arriveranno con le organizzazioni esterne. Testo, non enum. */
    @Column(nullable = false, length = 20)
    private String tipo = STRUTTURA;

    /** Struttura sopra questa nell'albero; null per le radici. */
    private Long padreId;

    @Column(nullable = false)
    private boolean attiva = true;

    public Long getId() { return id; }
    public String getCodice() { return codice; }
    public void setCodice(String codice) { this.codice = codice; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCodiceFiscale() { return codiceFiscale; }
    public void setCodiceFiscale(String codiceFiscale) { this.codiceFiscale = codiceFiscale; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Long getPadreId() { return padreId; }
    public void setPadreId(Long padreId) { this.padreId = padreId; }
    public boolean isAttiva() { return attiva; }
    public void setAttiva(boolean attiva) { this.attiva = attiva; }
}
