package com.eligesoft.orditogram.accesso;

import jakarta.persistence.*;

/**
 * Chi può fare che cosa su un processo, deciso nello Studio senza ripubblicare:
 *  - AVVIA: se il processo ha regole AVVIA, sostituiscono i candidateStarterGroups del BPMN;
 *  - VEDE: vedere tutte le pratiche del processo, entro un ambito (tutte, propria struttura, struttura e sotto);
 *  - ESPORTA: se il processo ha regole ESPORTA, solo loro esportano le sue pratiche dall'elenco.
 * Il soggetto è un gruppo (anche del provider di accesso) o un ruolo applicativo. Tutto salvato come testo.
 */
@Entity
@Table(name = "permesso_processo")
public class PermessoProcesso {

    public static final String AVVIA = "AVVIA";
    public static final String VEDE = "VEDE";
    public static final String ESPORTA = "ESPORTA";

    public static final String GRUPPO = "GRUPPO";
    public static final String RUOLO = "RUOLO";

    public static final String TUTTE = "TUTTE";
    public static final String STRUTTURA = "STRUTTURA";
    public static final String STRUTTURA_E_SOTTO = "STRUTTURA_E_SOTTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chiave del processo (id del process nel BPMN): vale per tutte le versioni. */
    @Column(nullable = false, length = 255)
    private String processo;

    @Column(nullable = false, length = 20)
    private String azione;

    @Column(nullable = false, length = 20)
    private String tipoSoggetto;

    @Column(nullable = false, length = 100)
    private String soggetto;

    @Column(nullable = false, length = 30)
    private String ambito = TUTTE;

    public PermessoProcesso() { }

    public PermessoProcesso(String processo, String azione, String tipoSoggetto, String soggetto, String ambito) {
        this.processo = processo;
        this.azione = azione;
        this.tipoSoggetto = tipoSoggetto;
        this.soggetto = soggetto;
        this.ambito = ambito;
    }

    public Long getId() { return id; }
    public String getProcesso() { return processo; }
    public String getAzione() { return azione; }
    public String getTipoSoggetto() { return tipoSoggetto; }
    public String getSoggetto() { return soggetto; }
    public String getAmbito() { return ambito; }
}
