package com.eligesoft.orditogram.user;

import jakarta.persistence.*;

/** Un attore della demo portato da una soluzione: chi è (ruolo) e in che ordine compare negli accessi rapidi. */
@Entity
@Table(name = "utente_prova")
public class UtenteProva {

    @Id
    @Column(length = 100)
    private String username;

    @Column(nullable = false, length = 80)
    private String ruolo;

    @Column(nullable = false)
    private int ordine;

    /** Chiave del processo del pacchetto che l'ha portato. */
    @Column(length = 200)
    private String origine;

    protected UtenteProva() { }

    public UtenteProva(String username, String ruolo, int ordine, String origine) {
        this.username = username;
        this.ruolo = ruolo;
        this.ordine = ordine;
        this.origine = origine;
    }

    public String getUsername() { return username; }
    public String getRuolo() { return ruolo; }
    public int getOrdine() { return ordine; }
    public String getOrigine() { return origine; }
}
