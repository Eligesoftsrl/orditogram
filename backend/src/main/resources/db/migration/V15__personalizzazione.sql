-- Personalizzazione dell'installazione: nome dell'applicazione e logo del cliente (Studio → Personalizzazione).
-- Una sola riga, solo se qualcosa è cambiato: senza, l'applicazione si chiama Orditogram e ha il suo marchio.

CREATE TABLE personalizzazione (
    id INTEGER NOT NULL,
    nome VARCHAR(60),
    logo_tipo VARCHAR(40),
    logo TEXT,
    aggiornata_il TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    aggiornata_da VARCHAR(100),
    CONSTRAINT pk_personalizzazione PRIMARY KEY (id),
    CONSTRAINT ck_personalizzazione_riga CHECK (id = 1)
);
