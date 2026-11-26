package com.eligesoft.orditogram.glossario;

import java.util.Arrays;
import java.util.Optional;

/**
 * Concetti comuni che ogni installazione chiama con le sue parole (Studio → Glossario). I predefiniti sono quelli
 * della pubblica amministrazione; un'azienda sceglie ad esempio "azienda", "Assistenza" e "cliente".
 * Il genere serve all'interfaccia per gli articoli ("dell'ente", "della società").
 */
public enum Termine {
    ENTE("ente", "Chi usa Orditogram", "Es. ente, azienda, studio, comune", "ente", "enti", "M"),
    UFFICIO("ufficio", "Il personale visto da fuori",
        "Il nome con cui gli utenti esterni vedono le azioni del personale. Es. Ufficio, Assistenza, Segreteria",
        "Ufficio", "Uffici", "M"),
    ESTERNO("esterno", "Chi entra da fuori", "Es. cittadino, cliente, fornitore, socio", "cittadino", "cittadini", "M");

    private final String chiave;
    private final String nome;
    private final String aiuto;
    private final String singolare;
    private final String plurale;
    private final String genere;

    Termine(String chiave, String nome, String aiuto, String singolare, String plurale, String genere) {
        this.chiave = chiave;
        this.nome = nome;
        this.aiuto = aiuto;
        this.singolare = singolare;
        this.plurale = plurale;
        this.genere = genere;
    }

    public String chiave() { return chiave; }
    public String nome() { return nome; }
    public String aiuto() { return aiuto; }
    public String singolare() { return singolare; }
    public String plurale() { return plurale; }
    public String genere() { return genere; }

    public static Optional<Termine> da(String chiave) {
        return Arrays.stream(values()).filter(t -> t.chiave.equals(chiave)).findFirst();
    }
}
