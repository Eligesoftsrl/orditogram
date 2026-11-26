package com.eligesoft.orditogram.modulo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Funzioni legate a un paese o a un settore, che un'installazione può spegnere (Studio → Moduli). Il nucleo di
 * Orditogram (processi, form, anagrafiche, permessi, API) funziona sempre; i moduli aggiungono regole specifiche.
 * Di base sono tutti accesi, così le installazioni esistenti non cambiano.
 */
public enum Modulo {
    ITALIA("italia", "Italia",
        "Regole italiane: festività nazionali nei termini in giorni lavorativi, formati codice fiscale, partita IVA, CAP e telefono.",
        List.of("Festività nazionali italiane (Pasqua compresa) nei calendari", "Formati dei campi: codice fiscale, partita IVA, CAP, telefono")),
    FIRMA_DIGITALE("firma-digitale", "Firma digitale",
        "Verifica dei documenti firmati: buste CAdES (.p7m) e PDF firmati (PAdES), firmatari e certificati.",
        List.of("Verifica della firma al caricamento dei documenti", "Contenuto estratto dalle buste .p7m")),
    PA("pa", "Pubblica amministrazione",
        "Il pacchetto di settore della PA: servizio di prova del protocollo per il connettore API e gli esempi della PA.",
        List.of("Servizio di prova /api/demo/protocollo", "Esempi: accesso agli atti (legge 241/1990), reclamo con controparte"));

    private final String codice;
    private final String nome;
    private final String descrizione;
    private final List<String> porta;

    Modulo(String codice, String nome, String descrizione, List<String> porta) {
        this.codice = codice;
        this.nome = nome;
        this.descrizione = descrizione;
        this.porta = porta;
    }

    public String codice() { return codice; }
    public String nome() { return nome; }
    public String descrizione() { return descrizione; }
    /** Cosa porta il modulo, in parole semplici (per lo Studio). */
    public List<String> porta() { return porta; }

    public static Optional<Modulo> da(String codice) {
        return Arrays.stream(values()).filter(m -> m.codice.equals(codice)).findFirst();
    }
}
