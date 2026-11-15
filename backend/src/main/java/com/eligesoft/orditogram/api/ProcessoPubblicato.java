package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.form.ValoriForm;

import java.util.List;

/**
 * Un processo pubblicato descritto per chi integra (vedi api/DescrizioneProcessi):
 * dati di avvio dal form, modalità degli allegati ("nessuno", "facoltativi", "obbligatori"), messaggi ricevibili.
 */
public record ProcessoPubblicato(String chiave, String nome, String descrizione, int versione, String definizioneId,
                                 String titoloForm, List<ValoriForm.Campo> campi, String allegati, List<Messaggio> messaggi) {

    /** Un messaggio che il processo può ricevere e i dati che porta. */
    public record Messaggio(String nome, List<String> dati) {}
}
