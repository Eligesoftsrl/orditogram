-- Utenti di prova portati da una soluzione (utenti-prova.json del pacchetto): gli attori della demo, con il ruolo
-- da mostrare negli accessi rapidi della pagina di accesso. Si creano solo con gli utenti di prova accesi.

CREATE TABLE utente_prova (
    username VARCHAR(100) NOT NULL,
    ruolo VARCHAR(80) NOT NULL,
    ordine INTEGER NOT NULL,
    origine VARCHAR(200),
    CONSTRAINT pk_utente_prova PRIMARY KEY (username)
);
