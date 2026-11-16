package com.eligesoft.orditogram.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Permessi che si possono dare a una chiave API. */
public final class Ambiti {

    public static final String PRATICHE_AVVIA = "pratiche:avvia";
    public static final String PRATICHE_LEGGI = "pratiche:leggi";
    public static final String DOCUMENTI_LEGGI = "documenti:leggi";
    public static final String DOCUMENTI_SCRIVI = "documenti:scrivi";
    public static final String MESSAGGI_INVIA = "messaggi:invia";
    public static final String ANAGRAFICHE_LEGGI = "anagrafiche:leggi";
    public static final String ANAGRAFICHE_SCRIVI = "anagrafiche:scrivi";

    /** Ambito → descrizione per lo Studio e per la documentazione. */
    public static final Map<String, String> TUTTI;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(PRATICHE_AVVIA, "Avviare nuove pratiche");
        m.put(PRATICHE_LEGGI, "Leggere stato, dati e storico delle pratiche");
        m.put(DOCUMENTI_LEGGI, "Elencare e scaricare i documenti delle pratiche");
        m.put(DOCUMENTI_SCRIVI, "Aggiungere documenti alle pratiche in corso");
        m.put(MESSAGGI_INVIA, "Inviare messaggi alle pratiche che li aspettano");
        m.put(ANAGRAFICHE_LEGGI, "Cercare e leggere le schede delle anagrafiche indicate, e sceglierle nelle pratiche");
        m.put(ANAGRAFICHE_SCRIVI, "Creare e modificare le schede delle anagrafiche indicate");
        TUTTI = java.util.Collections.unmodifiableMap(m);
    }

    private Ambiti() {}
}
