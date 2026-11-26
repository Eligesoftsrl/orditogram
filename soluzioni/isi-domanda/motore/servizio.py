"""Servizio ISI: il motore di calcolo e i registri del bando dietro un'API HTTP chiamata dai connettori di Orditogram.

Soluzione del cliente (non nucleo di Orditogram). Solo libreria standard di Python 3.11+.

    python3 servizio.py --porta 8095 --db isi.sqlite
    ORDITOGRAM_URL=http://localhost:8080 ORDITOGRAM_CHIAVE=iter_... python3 servizio.py   # avvisa le pratiche

Ogni risposta è un oggetto JSON piatto con valori di testo: i connettori `apiConnector` li copiano nei dati della
pratica con la mappatura `dato = campo`. Errori di chi chiama: 400/404/409 con {"errore": {codice, messaggio}}.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import secrets
import sqlite3
import threading
import time
import urllib.request
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import calcolo

ALFABETO_CODICE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # senza 0/O, 1/I (H7)
CARTELLA = Path(__file__).parent

SCHEMA = """
CREATE TABLE IF NOT EXISTS domanda (
  numero TEXT PRIMARY KEY,
  pratica TEXT,
  annualita TEXT NOT NULL,
  impresa TEXT NOT NULL,
  regione TEXT NOT NULL,
  asse TEXT NOT NULL,
  contributo TEXT NOT NULL,
  importo_progetto TEXT NOT NULL,
  data_iscrizione TEXT,
  fatturato TEXT,
  rating_legalita INTEGER NOT NULL DEFAULT 0,
  ts_registrazione INTEGER NOT NULL,
  stato TEXT NOT NULL,
  codice TEXT UNIQUE,
  codice_usato_il INTEGER,
  ts_invio INTEGER,
  posizione INTEGER,
  esito TEXT,
  fotografia TEXT NOT NULL,
  versione_regole TEXT NOT NULL,
  hash TEXT NOT NULL,
  UNIQUE (impresa, annualita)                       -- RF-05 CA-02: una sola domanda registrata
);
CREATE TABLE IF NOT EXISTS transizione (
  numero TEXT NOT NULL, da TEXT, a TEXT NOT NULL, ts INTEGER NOT NULL, chi TEXT NOT NULL, motivo TEXT
);
CREATE TABLE IF NOT EXISTS invio_sportello (
  ts INTEGER NOT NULL, numero TEXT, esito TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS elenco (
  annualita TEXT NOT NULL, tipo TEXT NOT NULL, pubblicato_il INTEGER NOT NULL, righe TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS istruttoria (
  numero TEXT PRIMARY KEY, avviata_il TEXT NOT NULL, giorni_sospesi INTEGER NOT NULL DEFAULT 0, sospesa_dal TEXT
);
"""


class ErroreRichiesta(Exception):
    def __init__(self, stato: int, codice: str, messaggio: str):
        super().__init__(messaggio)
        self.stato, self.codice, self.messaggio = stato, codice, messaggio


def adesso_ms() -> int:
    return time.time_ns() // 1_000_000


def iso_ms(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000, timezone.utc).astimezone().isoformat(timespec="milliseconds")


class Registro:
    """Registri del bando in SQLite: domande registrate, codici, invii allo sportello, elenchi, istruttorie."""

    def __init__(self, percorso: str, regole: dict, oggi=date.today, calendario_libero=True):
        self.db = sqlite3.connect(percorso, check_same_thread=False, isolation_level=None)
        self.db.executescript(SCHEMA)
        self.lock = threading.Lock()
        self.regole = regole
        self.oggi = oggi
        self.calendario_libero = calendario_libero  # True solo in sviluppo e collaudo: lo sportello è sempre aperto

    # -------------------------------------------------------------- registrazione (RF-20)
    def registra(self, d: dict) -> dict:
        r = calcolo.calcola(d, self.regole)
        if not r.registrabile:
            return {"esitoRegistrazione": "NON_REGISTRABILE", "esitoElenco": "", "messaggioRegistrazione": r.errori[0]}
        impresa = calcolo.testo(d.get("codiceFiscaleImpresa")).upper()
        if not impresa:
            raise ErroreRichiesta(400, "DATI_NON_VALIDI", "Manca il codice fiscale dell'impresa")
        ts = adesso_ms()
        fotografia = json.dumps({"dati": {k: v for k, v in d.items() if not k.startswith("_")},
                                 "risultato": r.in_testo(self.regole)}, ensure_ascii=False, sort_keys=True)
        numero = f"ISI{self.regole['annualita']}-{secrets.randbelow(10**8):08d}"
        with self.lock:
            try:
                self.db.execute(
                    "INSERT INTO domanda (numero, pratica, annualita, impresa, regione, asse, contributo, importo_progetto,"
                    " data_iscrizione, fatturato, rating_legalita, ts_registrazione, stato, fotografia, versione_regole, hash)"
                    " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (numero, calcolo.testo(d.get("pratica")), self.regole["annualita"], impresa,
                     calcolo.testo(d.get("regione")), calcolo.codice_asse(d.get("asse")),
                     str(r.importi["contributoTotale"]), str(r.importi["spesaAmmissibile"]),
                     calcolo.testo(d.get("dataIscrizioneCciaa")), calcolo.testo(d.get("fatturato")),
                     1 if calcolo.si(d.get("ratingLegalita")) else 0, ts, "REGISTRATA", fotografia,
                     self.regole["versioneRegole"], hashlib.sha256(fotografia.encode()).hexdigest()))
            except sqlite3.IntegrityError:
                return {"esitoRegistrazione": "GIA_REGISTRATA", "esitoElenco": "",
                        "messaggioRegistrazione": "L'impresa ha già registrato una domanda per questo Avviso"}
            self._transizione(numero, "BOZZA", "REGISTRATA", "impresa", "REGISTRA", ts)
        return {"esitoRegistrazione": "REGISTRATA", "esitoElenco": "", "numeroDomanda": numero, "dataOraRegistrazione": iso_ms(ts),
                "messaggioRegistrazione": "Domanda registrata", "versioneRegole": self.regole["versioneRegole"],
                "gruppoSede": self.regole["sedi"].get(calcolo.testo(d.get("regione")), self.regole["sedePredefinita"])}

    def _transizione(self, numero, da, a, chi, motivo, ts=None):
        self.db.execute("INSERT INTO transizione VALUES (?,?,?,?,?,?)", (numero, da, a, ts or adesso_ms(), chi, motivo))

    def _domanda(self, numero: str) -> sqlite3.Row:
        self.db.row_factory = sqlite3.Row
        row = self.db.execute("SELECT * FROM domanda WHERE numero = ?", (numero,)).fetchone()
        if row is None:
            raise ErroreRichiesta(404, "DOMANDA_NON_TROVATA", "Domanda non trovata")
        return row

    def cambia_stato(self, numero: str, stato: str, chi="sistema", motivo="") -> dict:
        ammessi = {"DECADUTA", "RINUNCIATA", "DOCUMENTI_INVIATI", "IN_ISTRUTTORIA", "AMMESSA", "AMMESSA_PARZIALMENTE",
                   "RESPINTA", "ANNULLATA"}
        if stato not in ammessi:
            raise ErroreRichiesta(400, "STATO_NON_VALIDO", f"Stato non previsto: {stato}")
        with self.lock:
            prima = self._domanda(numero)["stato"]
            self.db.execute("UPDATE domanda SET stato = ? WHERE numero = ?", (stato, numero))
            self._transizione(numero, prima, stato, chi, motivo)
        return {"statoDomanda": stato}

    # -------------------------------------------------------------- codice identificativo (RF-21)
    def codice(self, numero: str) -> dict:
        with self.lock:
            row = self._domanda(numero)
            if row["codice"]:
                if row["codice_usato_il"]:
                    raise ErroreRichiesta(409, "CODICE_GIA_USATO", "Il codice è già stato inviato allo sportello")
                return {"codiceIdentificativo": row["codice"]}
            if row["stato"] != "REGISTRATA":
                raise ErroreRichiesta(409, "STATO_NON_VALIDO", "Il codice si acquisisce solo per una domanda registrata")
            while True:
                c = "".join(secrets.choice(ALFABETO_CODICE) for _ in range(12))
                try:
                    self.db.execute("UPDATE domanda SET codice = ?, stato = 'CODICE_RILASCIATO' WHERE numero = ?", (c, numero))
                    break
                except sqlite3.IntegrityError:
                    continue
            self._transizione(numero, "REGISTRATA", "CODICE_RILASCIATO", "impresa", "Acquisizione del codice")
        return {"codiceIdentificativo": c}

    # -------------------------------------------------------------- sportello (RF-25)
    def sportello(self, numero: str, codice: str) -> dict:
        ts = adesso_ms()  # l'ora si prende per prima, dall'orologio del server (CA-02)
        codice = calcolo.testo(codice).upper().replace(" ", "")
        if not self.calendario_libero:
            cal = self.regole["calendario"]
            ora = datetime.fromtimestamp(ts / 1000, timezone.utc)
            apre, chiude = datetime.fromisoformat(cal["aperturaSportello"]), datetime.fromisoformat(cal["chiusuraSportello"])
            if not apre <= ora <= chiude:
                self.db.execute("INSERT INTO invio_sportello VALUES (?,?,?)", (ts, numero, "FUORI_FINESTRA"))
                prossima = apre.strftime("%d/%m/%Y alle %H:%M") if ora < apre else "nessuna: lo sportello è chiuso"
                return {"esitoInvio": "RIFIUTATO", "messaggioInvio": f"Sportello chiuso. Prossima apertura: {prossima}"}
        with self.lock:
            self.db.row_factory = sqlite3.Row
            row = self.db.execute("SELECT * FROM domanda WHERE numero = ? AND codice = ?", (numero, codice)).fetchone()
            if row is None or row["codice_usato_il"] or row["stato"] != "CODICE_RILASCIATO":
                self.db.execute("INSERT INTO invio_sportello VALUES (?,?,?)", (ts, numero, "RIFIUTATO"))
                # stesso messaggio per codice inesistente, di altri o già usato (CA-04, CA-05)
                return {"esitoInvio": "RIFIUTATO", "messaggioInvio": "Codice non valido o già utilizzato"}
            self.db.execute("UPDATE domanda SET ts_invio = ?, codice_usato_il = ?, stato = 'INVIATA_SPORTELLO' "
                            "WHERE numero = ?", (ts, ts, numero))
            self.db.execute("INSERT INTO invio_sportello VALUES (?,?,?)", (ts, numero, "PRESO_IN_CARICO"))
            self._transizione(numero, "CODICE_RILASCIATO", "INVIATA_SPORTELLO", "impresa", "Invio allo sportello", ts)
        return {"esitoInvio": "INVIATA", "dataOraInvio": iso_ms(ts),
                "messaggioInvio": f"Codice preso in carico il {iso_ms(ts)}"}

    # -------------------------------------------------------------- elenchi (RF-53, RF-54, RF-55)
    def _stanziamento(self, regione, asse) -> Decimal:
        return Decimal(str(self.regole["stanziamenti"].get(regione, {}).get(asse, 0)))

    def _righe(self, stati: tuple) -> list[sqlite3.Row]:
        self.db.row_factory = sqlite3.Row
        q = f"SELECT * FROM domanda WHERE annualita = ? AND stato IN ({','.join('?' * len(stati))})"
        return self.db.execute(q, (self.regole["annualita"], *stati)).fetchall()

    def calcola_elenco(self, tipo: str) -> list[dict]:
        righe = []
        gruppi: dict[tuple, list] = {}
        if tipo == "NCD":
            for r in self._righe(("REGISTRATA", "CODICE_RILASCIATO", "INVIATA_SPORTELLO")):
                gruppi.setdefault((r["regione"], r["asse"]), []).append(r)
            for (reg, asse), lista in gruppi.items():
                if sum(Decimal(r["contributo"]) for r in lista) <= self._stanziamento(reg, asse):
                    for pos, r in enumerate(sorted(lista, key=lambda x: x["ts_registrazione"]), 1):
                        righe.append(self._riga(r, pos, "NCD"))
        elif tipo == "PROVVISORIO":
            for r in self._righe(("INVIATA_SPORTELLO",)):  # mai inviate allo sportello: fuori (CA-03)
                gruppi.setdefault((r["regione"], r["asse"]), []).append(r)
            for (reg, asse), lista in gruppi.items():
                lista.sort(key=self._precedenza)
                residuo = self._stanziamento(reg, asse)
                for pos, r in enumerate(lista, 1):
                    c = Decimal(r["contributo"])
                    if c <= residuo:  # H3: chi supera il residuo è escluso, si prosegue
                        residuo -= c
                        righe.append(self._riga(r, pos, "IN_POSIZIONE_UTILE"))
                    else:
                        righe.append(self._riga(r, pos, "NON_AMMISSIBILE_FONDI"))
        elif tipo == "DEFINITIVO":
            for r in self._righe(("NCD", "IN_POSIZIONE_UTILE", "SUBENTRATA", "DOCUMENTI_INVIATI", "IN_ISTRUTTORIA",
                                  "NON_AMMISSIBILE_FONDI", "DECADUTA", "RINUNCIATA")):
                gruppi.setdefault((r["regione"], r["asse"]), []).append(r)
            for (reg, asse), lista in gruppi.items():
                lista.sort(key=lambda x: (x["posizione"] or 10**9, *self._precedenza(x)))
                impegnato = sum(Decimal(r["contributo"]) for r in lista
                                if r["stato"] not in ("NON_AMMISSIBILE_FONDI", "DECADUTA", "RINUNCIATA"))
                residuo = self._stanziamento(reg, asse) - impegnato
                for r in lista:
                    esito = {"DECADUTA": "DECADUTA", "RINUNCIATA": "RINUNCIATA"}.get(r["stato"], "AMMISSIBILE")
                    if r["stato"] == "NON_AMMISSIBILE_FONDI":
                        c = Decimal(r["contributo"])
                        if c <= residuo:
                            residuo -= c
                            esito = "SUBENTRATA"
                        else:
                            esito = "NON_AMMISSIBILE_FONDI"
                    righe.append(self._riga(r, r["posizione"], esito))
        else:
            raise ErroreRichiesta(400, "TIPO_NON_VALIDO", "Tipo di elenco: NCD, PROVVISORIO o DEFINITIVO")
        return righe

    @staticmethod
    def _precedenza(r) -> tuple:
        """Ora di arrivo, poi i criteri 9.10."""
        rating = -r["rating_legalita"] if Decimal(r["fatturato"] or 0) > 2_000_000 else 0
        return (r["ts_invio"] or 10**15, r["ts_registrazione"], Decimal(r["contributo"]),
                -Decimal(r["importo_progetto"]), r["data_iscrizione"] or "9999", rating)

    def _riga(self, r, pos, esito) -> dict:
        return {"numero": r["numero"], "pratica": r["pratica"], "regione": r["regione"], "asse": r["asse"],
                "posizione": pos, "importo": calcolo.tronca_euro(Decimal(r["contributo"])), "esito": esito}

    def anteprima(self, tipo: str) -> dict:
        with self.lock:
            righe = self.calcola_elenco(tipo)
        return {"tipoElenco": tipo, "righeElenco": str(len(righe)), "riepilogoElenco": self._testo(righe)}

    def pubblica(self, tipo: str, avvisa=None) -> dict:
        termine = (datetime.now().astimezone() + timedelta(days=self.regole["calendario"]["giorniCaricamentoDocumenti"]))
        with self.lock:
            righe = self.calcola_elenco(tipo)
            stato_da_esito = {"NCD": "NCD", "IN_POSIZIONE_UTILE": "IN_POSIZIONE_UTILE",
                              "NON_AMMISSIBILE_FONDI": "NON_AMMISSIBILE_FONDI", "SUBENTRATA": "SUBENTRATA"}
            for x in righe:
                if x["esito"] in stato_da_esito:
                    prima = self._domanda(x["numero"])["stato"]
                    if prima != stato_da_esito[x["esito"]]:
                        self.db.execute("UPDATE domanda SET stato = ?, posizione = ?, esito = ? WHERE numero = ?",
                                        (stato_da_esito[x["esito"]], x["posizione"], x["esito"], x["numero"]))
                        self._transizione(x["numero"], prima, stato_da_esito[x["esito"]], "sistema", f"Elenco {tipo}")
            self.db.execute("INSERT INTO elenco VALUES (?,?,?,?)", (self.regole["annualita"], tipo, adesso_ms(),
                                                                    json.dumps(righe, ensure_ascii=False)))
        avvisate = 0
        if avvisa:
            for x in righe:
                if x["esito"] in ("NCD", "IN_POSIZIONE_UTILE", "NON_AMMISSIBILE_FONDI", "SUBENTRATA"):
                    avvisate += avvisa(x, termine.isoformat(timespec="seconds"))
        return {"tipoElenco": tipo, "righeElenco": str(len(righe)), "praticheAvvisate": str(avvisate),
                "riepilogoElenco": self._testo(righe), "termineDocumenti": termine.isoformat(timespec="seconds")}

    def elenchi_pubblicati(self) -> list[dict]:
        self.db.row_factory = sqlite3.Row
        return [{"tipo": r["tipo"], "pubblicatoIl": iso_ms(r["pubblicato_il"]), "righe": json.loads(r["righe"])}
                for r in self.db.execute("SELECT * FROM elenco ORDER BY pubblicato_il")]

    @staticmethod
    def _testo(righe: list[dict]) -> str:
        if not righe:
            return "Nessuna domanda in questo elenco"
        return "\n".join(f"{x['regione']} · Asse {x['asse']} · {x['posizione']}. {x['numero']} · "
                         f"{x['importo']:,} € · {x['esito']}".replace(",", ".") for x in righe)

    # -------------------------------------------------------------- istruttoria: 120 giorni con sospensione (RF-57 CA-04)
    def istruttoria(self, numero: str, azione: str) -> dict:
        oggi = self.oggi()
        p = self.regole["parametri"]
        with self.lock:
            self.db.row_factory = sqlite3.Row
            row = self.db.execute("SELECT * FROM istruttoria WHERE numero = ?", (numero,)).fetchone()
            if azione == "avvia" and row is None:
                self.db.execute("INSERT INTO istruttoria (numero, avviata_il) VALUES (?, ?)", (numero, oggi.isoformat()))
            elif row is None:
                raise ErroreRichiesta(404, "ISTRUTTORIA_NON_AVVIATA", "Istruttoria non avviata")
            elif azione == "sospendi" and not row["sospesa_dal"]:
                self.db.execute("UPDATE istruttoria SET sospesa_dal = ? WHERE numero = ?", (oggi.isoformat(), numero))
            elif azione == "riprendi" and row["sospesa_dal"]:
                giorni = (oggi - date.fromisoformat(row["sospesa_dal"])).days
                self.db.execute("UPDATE istruttoria SET giorni_sospesi = giorni_sospesi + ?, sospesa_dal = NULL "
                                "WHERE numero = ?", (giorni, numero))
            row = self.db.execute("SELECT * FROM istruttoria WHERE numero = ?", (numero,)).fetchone()
        avvio = date.fromisoformat(row["avviata_il"])
        scadenza = avvio + timedelta(days=p["giorniIstruttoria"] + row["giorni_sospesi"])
        avviso = avvio + timedelta(days=p["giorniAvvisoIstruttoria"] + row["giorni_sospesi"])
        return {"scadenzaIstruttoria": f"{scadenza.isoformat()}T18:00:00", "avvisoIstruttoria": f"{avviso.isoformat()}T09:00:00",
                "giorniSospesi": str(row["giorni_sospesi"]),
                "giorniRimanenti": str(max(0, (scadenza - oggi).days))}


# ------------------------------------------------------------------ servizi finti (mock) e controlli semplici

def incarico_tecnico(d: dict) -> dict:
    """RF-40 CA-02: il tecnico non può essere titolare, legale rappresentante, amministratore o socio."""
    cf = calcolo.testo(d.get("cfTecnico")).upper()
    vietati = {calcolo.testo(d.get("cfLegaleRappresentante")).upper()}
    vietati |= {x.strip().upper() for x in calcolo.testo(d.get("cfSoci")).replace(";", ",").replace("\n", ",").split(",")}
    vietati.discard("")
    if len(cf) != 16:
        return {"incaricoAmmesso": "No", "motivoIncarico": "Codice fiscale del tecnico non valido"}
    if cf in vietati:
        return {"incaricoAmmesso": "No", "motivoIncarico": "Il tecnico non può essere titolare, legale rappresentante, "
                                                         "amministratore o socio dell'impresa (RF-40)"}
    return {"incaricoAmmesso": "Sì", "motivoIncarico": "Incarico ammesso"}


def anagrafica_inail(cf: str) -> dict:
    """RF-71, finto: imprese di prova in dati-prova/imprese.json."""
    imprese = json.loads((CARTELLA / "dati-prova" / "imprese.json").read_text(encoding="utf-8"))
    dati = imprese.get(cf.strip().upper())
    if dati is None:
        return {"anagraficaTrovata": "No", "avvisoAnagrafica": "Impresa non trovata nell'anagrafica INAIL: compila i dati a mano"}
    return {"anagraficaTrovata": "Sì", "avvisoAnagrafica": "Dati letti dall'anagrafica INAIL", **dati}


def avvisa_orditogram(url: str, chiave: str):
    """Consegna l'esito dell'elenco alla pratica che lo aspetta (messaggio esitoElenco)."""
    def invia(riga: dict, termine: str) -> int:
        corpo = json.dumps({"cerca": {"numeroDomanda": riga["numero"]},
                            "dati": {"esitoElenco": riga["esito"], "posizioneElenco": str(riga["posizione"]),
                                     "termineDocumenti": termine}}).encode()
        req = urllib.request.Request(f"{url.rstrip('/')}/api/messaggi/esitoElenco", data=corpo, method="POST",
                                     headers={"Content-Type": "application/json", "X-API-Key": chiave})
        try:
            with urllib.request.urlopen(req, timeout=10):
                return 1
        except Exception as e:  # la pratica può non aspettare ancora (es. rinunciata): si annota e si prosegue
            print(f"Avviso non consegnato a {riga['numero']}: {e}", flush=True)
            return 0
    return invia


# ------------------------------------------------------------------ HTTP

def crea_gestore(registro: Registro, avvisa=None, token: str | None = None):
    regole = registro.regole
    pubblici = {"/salute", "/elenchi", "/calendario"}

    class Gestore(BaseHTTPRequestHandler):
        server_version = "ServizioISI/1.0"

        def log_message(self, fmt, *args):
            print(json.dumps({"ora": datetime.now().isoformat(timespec="milliseconds"), "richiesta": fmt % args}), flush=True)

        def _rispondi(self, stato: int, corpo: dict):
            dati = json.dumps(corpo, ensure_ascii=False).encode()
            self.send_response(stato)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(dati)))
            self.end_headers()
            self.wfile.write(dati)

        def _corpo(self) -> dict:
            n = int(self.headers.get("Content-Length") or 0)
            if n > 1_000_000:
                raise ErroreRichiesta(413, "TROPPO_GRANDE", "Richiesta troppo grande")
            try:
                return json.loads(self.rfile.read(n) or b"{}")
            except json.JSONDecodeError:
                raise ErroreRichiesta(400, "JSON_NON_VALIDO", "Il corpo non è un JSON valido")

        def _gestisci(self, metodo: str):
            url = urlparse(self.path)
            q = {k: v[0] for k, v in parse_qs(url.query).items()}
            try:
                if q.get("simula") == "errore":
                    raise ErroreRichiesta(503, "NON_DISPONIBILE", "Servizio non disponibile (simulato)")
                p = url.path.rstrip("/")
                if token and p not in pubblici and not secrets.compare_digest(
                        self.headers.get("Authorization", ""), f"Bearer {token}"):
                    raise ErroreRichiesta(401, "NON_AUTORIZZATO", "Credenziale mancante o non valida")
                d = self._corpo() if metodo == "POST" else {}
                if p == "/salute":
                    return self._rispondi(200, {"stato": "ok", "versioneRegole": regole["versioneRegole"]})
                if p == "/calendario":
                    return self._rispondi(200, {k: str(v) for k, v in regole["calendario"].items()})
                if p == "/impresa":
                    return self._rispondi(200, anagrafica_inail(q.get("codiceFiscale", "")))
                if p == "/calcola":
                    r = calcolo.calcola(d, regole)
                    out = r.in_testo(regole)
                    asse = calcolo.codice_asse(d.get("asse"))
                    out["modelloPerizia"] = {"1.1": "B1.1", "2": "B2", "3": "B3", "4": "B4", "5.1": "B5", "5.2": "B5",
                                             "1.2": "Dichiarazione sostitutiva B1.2"}.get(asse, "")
                    out["gruppoSede"] = regole["sedi"].get(calcolo.testo(d.get("regione")), regole["sedePredefinita"])
                    return self._rispondi(200, out)
                if p == "/registra":
                    return self._rispondi(200, registro.registra(d))
                if p == "/codice":
                    return self._rispondi(200, registro.codice(calcolo.testo(d.get("numeroDomanda"))))
                if p == "/sportello":
                    return self._rispondi(200, registro.sportello(calcolo.testo(d.get("numeroDomanda")), d.get("codice")))
                if p == "/stato":
                    esiti = {"Ammetti": "AMMESSA", "Ammetti parzialmente": "AMMESSA_PARZIALMENTE", "Respingi": "RESPINTA"}
                    if not calcolo.testo(d.get("stato")) and calcolo.testo(d.get("esitoIstruttoria")) in esiti:
                        d["stato"] = esiti[calcolo.testo(d.get("esitoIstruttoria"))]
                    return self._rispondi(200, registro.cambia_stato(calcolo.testo(d.get("numeroDomanda")),
                                                                     calcolo.testo(d.get("stato")),
                                                                     calcolo.testo(d.get("chi")) or "sistema",
                                                                     calcolo.testo(d.get("motivo"))))
                if p in ("/elenchi/anteprima", "/elenchi/pubblica"):
                    tipo = calcolo.testo(d.get("tipoElenco") or q.get("tipo")).upper()
                    return self._rispondi(200, registro.anteprima(tipo) if p.endswith("anteprima")
                                          else registro.pubblica(tipo, avvisa))
                if p == "/elenchi":  # RF-30: pagina pubblica, solo dati pubblicati
                    return self._rispondi(200, {"elenchi": registro.elenchi_pubblicati()})
                if p.startswith("/istruttoria/"):
                    return self._rispondi(200, registro.istruttoria(calcolo.testo(d.get("numeroDomanda")), p.split("/")[-1]))
                if p == "/incarico":
                    return self._rispondi(200, incarico_tecnico(d))
                raise ErroreRichiesta(404, "NON_TROVATO", "Indirizzo sconosciuto")
            except ErroreRichiesta as e:
                self._rispondi(e.stato, {"errore": {"codice": e.codice, "messaggio": e.messaggio}})
            except Exception as e:  # niente dettagli a chi chiama
                codice = "E-" + secrets.token_hex(4).upper()
                print(json.dumps({"errore": codice, "dettaglio": repr(e)}), flush=True)
                self._rispondi(500, {"errore": {"codice": codice, "messaggio": "Errore imprevisto"}})

        def do_GET(self):
            self._gestisci("GET")

        def do_POST(self):
            self._gestisci("POST")

    return Gestore


def main():
    a = argparse.ArgumentParser(description="Servizio ISI (motore di calcolo e registri del bando)")
    a.add_argument("--porta", type=int, default=int(os.environ.get("ISI_PORTA", 8095)))
    a.add_argument("--db", default=os.environ.get("ISI_DB", "isi.sqlite"))
    a.add_argument("--annualita", default=os.environ.get("ISI_ANNUALITA", "2025"))
    args = a.parse_args()
    regole = calcolo.carica_regole(args.annualita)
    if os.environ.get("ISI_CALENDARIO_FILE"):  # solo collaudo: un calendario con date future
        regole["calendario"] = json.loads(Path(os.environ["ISI_CALENDARIO_FILE"]).read_text(encoding="utf-8"))
    libero = os.environ.get("ISI_CALENDARIO_LIBERO", "") == "1"
    registro = Registro(args.db, regole, calendario_libero=libero)
    if libero:
        print("ATTENZIONE: calendario libero, lo sportello accetta invii a qualsiasi ora (solo collaudo)", flush=True)
    url, chiave = os.environ.get("ORDITOGRAM_URL"), os.environ.get("ORDITOGRAM_CHIAVE")
    avvisa = avvisa_orditogram(url, chiave) if url and chiave else None
    token = os.environ.get("ISI_TOKEN")
    if not token:
        print("ATTENZIONE: ISI_TOKEN non impostato, il servizio risponde a chiunque (solo sviluppo)", flush=True)
    srv = ThreadingHTTPServer(("127.0.0.1", args.porta), crea_gestore(registro, avvisa, token))
    print(f"Servizio ISI {regole['versioneRegole']} su http://127.0.0.1:{args.porta}", flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
