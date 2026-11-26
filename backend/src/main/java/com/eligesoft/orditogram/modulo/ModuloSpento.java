package com.eligesoft.orditogram.modulo;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Un modulo spento dall'amministratore (gli altri sono accesi). */
@Entity
@Table(name = "modulo_spento")
public class ModuloSpento {

    @Id
    @Column(length = 40)
    private String codice;

    @Column(name = "spento_il", nullable = false)
    private OffsetDateTime spentoIl;

    @Column(name = "spento_da", length = 100)
    private String spentoDa;

    protected ModuloSpento() { }

    public ModuloSpento(String codice, OffsetDateTime spentoIl, String spentoDa) {
        this.codice = codice;
        this.spentoIl = spentoIl;
        this.spentoDa = spentoDa;
    }

    public String getCodice() { return codice; }
    public OffsetDateTime getSpentoIl() { return spentoIl; }
    public String getSpentoDa() { return spentoDa; }
}
