-- Moduli: funzioni legate a un paese o a un settore che l'installazione può spegnere (Studio → Moduli).
-- Di base sono tutti accesi: qui si salvano solo quelli spenti, con chi e quando.

CREATE TABLE modulo_spento (
    codice VARCHAR(40) NOT NULL,
    spento_il TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    spento_da VARCHAR(100),
    CONSTRAINT pk_modulo_spento PRIMARY KEY (codice)
);
