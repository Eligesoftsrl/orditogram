package com.eligesoft.orditogram.anagrafica;

import jakarta.persistence.*;

/**
 * Un valore di una scheda usato per cercarla: campi chiave, campi ricercabili e relazioni (l'id della scheda
 * collegata). Il valore è normalizzato (minuscolo, spazi ridotti). "chiave" è valorizzata solo per i campi chiave:
 * il vincolo UNIQUE del database impedisce due schede dello stesso tipo con lo stesso valore.
 */
@Entity
@Table(name = "anagrafica_indice")
public class IndiceAnagrafica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheda_id", nullable = false)
    private Long schedaId;

    @Column(name = "tipo_id", nullable = false)
    private Long tipoId;

    @Column(nullable = false, length = 100)
    private String campo;

    @Column(nullable = false, length = 255)
    private String valore;

    @Column(length = 255)
    private String chiave;

    public IndiceAnagrafica() { }

    public IndiceAnagrafica(Long schedaId, Long tipoId, String campo, String valore, boolean chiave) {
        this.schedaId = schedaId;
        this.tipoId = tipoId;
        this.campo = campo;
        this.valore = valore;
        this.chiave = chiave ? valore : null;
    }

    public Long getId() { return id; }
    public Long getSchedaId() { return schedaId; }
    public Long getTipoId() { return tipoId; }
    public String getCampo() { return campo; }
    public String getValore() { return valore; }
    public String getChiave() { return chiave; }
}
