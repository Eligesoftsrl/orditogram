-- Registri del Bando ISI (SQL valido su PostgreSQL e H2)
CREATE TABLE domanda (
  numero            VARCHAR(40)  PRIMARY KEY,
  pratica           VARCHAR(100),
  annualita         VARCHAR(10)  NOT NULL,
  impresa           VARCHAR(32)  NOT NULL,
  regione           VARCHAR(100) NOT NULL,
  asse              VARCHAR(5)   NOT NULL,
  contributo        VARCHAR(30)  NOT NULL,
  importo_progetto  VARCHAR(30)  NOT NULL,
  data_iscrizione   VARCHAR(10),
  fatturato         VARCHAR(30),
  rating_legalita   INTEGER      NOT NULL DEFAULT 0,
  ts_registrazione  BIGINT       NOT NULL,
  stato             VARCHAR(30)  NOT NULL,
  codice            VARCHAR(12)  UNIQUE,
  codice_usato_il   BIGINT,
  ts_invio          BIGINT,
  posizione         INTEGER,
  esito             VARCHAR(30),
  fotografia        TEXT         NOT NULL,
  versione_regole   VARCHAR(20)  NOT NULL,
  impronta          VARCHAR(64)  NOT NULL,
  CONSTRAINT domanda_unica UNIQUE (impresa, annualita)   -- RF-05 CA-02: una sola domanda registrata
);

CREATE TABLE transizione (
  numero    VARCHAR(40) NOT NULL,
  da_stato  VARCHAR(30),
  a_stato   VARCHAR(30) NOT NULL,
  ts        BIGINT      NOT NULL,
  chi       VARCHAR(100) NOT NULL,
  motivo    VARCHAR(500)
);
CREATE INDEX transizione_numero ON transizione (numero);

CREATE TABLE invio_sportello (
  ts      BIGINT      NOT NULL,
  numero  VARCHAR(40),
  esito   VARCHAR(30) NOT NULL
);

CREATE TABLE elenco (
  annualita      VARCHAR(10) NOT NULL,
  tipo           VARCHAR(20) NOT NULL,
  pubblicato_il  BIGINT      NOT NULL,
  righe          TEXT        NOT NULL
);

CREATE TABLE istruttoria (
  numero          VARCHAR(40) PRIMARY KEY,
  avviata_il      VARCHAR(10) NOT NULL,
  giorni_sospesi  INTEGER     NOT NULL DEFAULT 0,
  sospesa_dal     VARCHAR(10)
);
