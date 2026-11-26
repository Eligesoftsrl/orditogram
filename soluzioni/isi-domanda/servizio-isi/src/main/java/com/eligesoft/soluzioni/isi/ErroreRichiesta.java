package com.eligesoft.soluzioni.isi;

/** Errore di chi chiama: diventa {@code {"errore": {"codice", "messaggio"}}} con lo stato HTTP indicato. */
public class ErroreRichiesta extends RuntimeException {
    private final int stato;
    private final String codice;

    public ErroreRichiesta(int stato, String codice, String messaggio) {
        super(messaggio);
        this.stato = stato;
        this.codice = codice;
    }

    public int stato() { return stato; }
    public String codice() { return codice; }
}
