package com.eligesoft.orditogram.anagrafica;

import java.util.List;

/** Dati di una scheda che non rispettano la definizione: un errore per campo, tutti insieme. */
public class DatiNonValidi extends RuntimeException {

    public record Errore(String campo, String messaggio) { }

    private final transient List<Errore> errori;

    public DatiNonValidi(String messaggio, List<Errore> errori) {
        super(messaggio);
        this.errori = List.copyOf(errori);
    }

    public List<Errore> errori() { return errori; }
}
