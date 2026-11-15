package com.eligesoft.orditogram.webhook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Eventi a cui un sistema esterno si può iscrivere con un webhook. */
public final class TipiEvento {

    public static final String PRATICA_AVVIATA = "pratica.avviata";
    public static final String PRATICA_CONCLUSA = "pratica.conclusa";
    public static final String PRATICA_ANNULLATA = "pratica.annullata";
    public static final String ATTIVITA_CREATA = "attivita.creata";
    public static final String ATTIVITA_ASSEGNATA = "attivita.assegnata";
    public static final String ATTIVITA_COMPLETATA = "attivita.completata";
    public static final String MESSAGGIO_RICEVUTO = "messaggio.ricevuto";
    public static final String DOCUMENTO_AGGIUNTO = "documento.aggiunto";
    public static final String SCADENZA_SCATTATA = "scadenza.scattata";
    public static final String PRATICA_ERRORE = "pratica.errore";
    /** Solo per l'invio di prova dallo Studio. */
    public static final String PROVA = "prova";

    public static final Map<String, String> TUTTI;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(PRATICA_AVVIATA, "Una nuova pratica è partita (dall'interfaccia o dall'API)");
        m.put(PRATICA_CONCLUSA, "La pratica è arrivata alla fine del processo");
        m.put(PRATICA_ANNULLATA, "La pratica è stata chiusa prima della fine");
        m.put(ATTIVITA_CREATA, "Un'attività è pronta per essere lavorata da un utente o da un gruppo");
        m.put(ATTIVITA_ASSEGNATA, "Un utente ha preso in carico un'attività");
        m.put(ATTIVITA_COMPLETATA, "Un'attività è stata completata");
        m.put(MESSAGGIO_RICEVUTO, "La pratica ha ricevuto un messaggio atteso");
        m.put(DOCUMENTO_AGGIUNTO, "È stato caricato un documento o una sua nuova versione");
        m.put(SCADENZA_SCATTATA, "È scattato un termine, un sollecito o la fine di un'attesa");
        m.put(PRATICA_ERRORE, "Un passo automatico non è riuscito (es. un servizio esterno o un'email)");
        TUTTI = Collections.unmodifiableMap(m);
    }

    private TipiEvento() {}
}
