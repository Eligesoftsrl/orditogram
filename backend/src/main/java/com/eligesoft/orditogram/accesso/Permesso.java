package com.eligesoft.orditogram.accesso;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catalogo dei permessi applicativi. I ruoli (accesso/Ruolo) li raggruppano; nel database si salva il codice,
 * che non cambia mai. Un permesso nuovo va aggiunto qui: l'amministratore lo riceve da solo.
 */
public enum Permesso {
    STUDIO_PROCESSI("studio.processi", "Processi",
        "Disegnare e importare i processi come bozze; connettori e credenziali dei servizi"),
    STUDIO_PUBBLICA("studio.pubblica", "Pubblicazione",
        "Mettere in produzione i processi (senza, si preparano solo le bozze)"),
    STUDIO_FORM("studio.form", "Form", "Creare e modificare i form"),
    INTEGRAZIONI("integrazioni.gestisci", "Integrazioni", "Chiavi API e webhook per i sistemi esterni"),
    UTENTI("utenti.gestisci", "Utenti e ruoli", "Creare utenti, assegnare gruppi e ruoli"),
    REGISTRO("registro.leggi", "Registro", "Leggere il registro delle azioni di amministrazione"),
    PRATICHE_TUTTE("pratiche.vedi-tutte", "Tutte le pratiche",
        "Vedere tutte le pratiche, anche quelle a cui non partecipa (senza poterle lavorare)");

    private final String codice;
    private final String nome;
    private final String descrizione;

    Permesso(String codice, String nome, String descrizione) {
        this.codice = codice;
        this.nome = nome;
        this.descrizione = descrizione;
    }

    public String codice() { return codice; }
    public String nome() { return nome; }
    public String descrizione() { return descrizione; }

    public static Optional<Permesso> da(String codice) {
        return Arrays.stream(values()).filter(p -> p.codice.equals(codice)).findFirst();
    }
}
