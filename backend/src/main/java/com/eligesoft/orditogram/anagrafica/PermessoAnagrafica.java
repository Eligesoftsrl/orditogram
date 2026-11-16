package com.eligesoft.orditogram.anagrafica;

import jakarta.persistence.*;

/**
 * Chi può fare che cosa sulle schede di un tipo di anagrafica: VEDE (cercare e leggere), MODIFICA (creare,
 * modificare, cessare; comprende vedere), ESPORTA (scaricare l'elenco; comprende vedere). Il soggetto è un gruppo
 * (anche del provider di accesso) o un ruolo applicativo. Senza regole, solo l'amministratore vede le schede.
 */
@Entity
@Table(name = "permesso_anagrafica")
public class PermessoAnagrafica {

    public static final String VEDE = "VEDE";
    public static final String MODIFICA = "MODIFICA";
    public static final String ESPORTA = "ESPORTA";

    public static final String GRUPPO = "GRUPPO";
    public static final String RUOLO = "RUOLO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_id", nullable = false)
    private Long tipoId;

    @Column(nullable = false, length = 20)
    private String azione;

    @Column(nullable = false, length = 20)
    private String tipoSoggetto;

    @Column(nullable = false, length = 100)
    private String soggetto;

    public PermessoAnagrafica() { }

    public PermessoAnagrafica(Long tipoId, String azione, String tipoSoggetto, String soggetto) {
        this.tipoId = tipoId;
        this.azione = azione;
        this.tipoSoggetto = tipoSoggetto;
        this.soggetto = soggetto;
    }

    public Long getId() { return id; }
    public Long getTipoId() { return tipoId; }
    public String getAzione() { return azione; }
    public String getTipoSoggetto() { return tipoSoggetto; }
    public String getSoggetto() { return soggetto; }
}
