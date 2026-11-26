-- Glossario: le parole che ogni installazione sceglie per i concetti comuni (ente, ufficio, utente esterno).
-- Una riga solo per i termini cambiati; gli altri restano quelli predefiniti dell'applicazione.

CREATE TABLE glossario_termine (
    chiave VARCHAR(40) NOT NULL,
    singolare VARCHAR(60) NOT NULL,
    plurale VARCHAR(60) NOT NULL,
    genere VARCHAR(1) NOT NULL,
    CONSTRAINT pk_glossario_termine PRIMARY KEY (chiave),
    CONSTRAINT ck_glossario_genere CHECK (genere IN ('M', 'F'))
);
