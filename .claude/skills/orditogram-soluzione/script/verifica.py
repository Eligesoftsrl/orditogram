#!/usr/bin/env python3
"""
Verifica una soluzione Orditogram senza avviare il server.

    python3 verifica.py <cartella-soluzione> [--credenziali nome1,nome2] [--anagrafiche-esistenti a,b]
                        [--processi-pubblicati chiave1,chiave2]

La cartella contiene:
    processo.bpmn20.xml          il processo (uno solo)
    form/<formKey>.json          i form usati dal processo
    anagrafiche/<codice>.json    i tipi di anagrafica (facoltativo)
    permessi.json                chi avvia, vede, esporta (facoltativo)
    collaudo/scenari.json        gli scenari di collaudo (facoltativo, controllati nella forma)

Rifà le regole del backend (process/ProcessValidator, form/FormService, form/RegoleCampo,
anagrafica/DefinizioneAnagrafica, accesso/RegolePermessiProcesso) con gli stessi messaggi.
ERRORI: Orditogram rifiuterebbe la pubblicazione o l'importazione. AVVISI: si pubblica, ma va controllato.
Esce con 1 se ci sono errori.

Tieni questo file allineato al backend: ogni regola nuova dei validatori va aggiunta anche qui.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
ACT = "http://activiti.org/bpmn"
ORD = "http://orditogram.it/bpmn"
XSI = "http://www.w3.org/2001/XMLSchema-instance"
DI = "http://www.omg.org/spec/BPMN/20100524/DI"

DATI_DI_SISTEMA = {"avviataDa", "avviataDaUtente", "chiusaDa", "chiusaIl"}
SUPPORTATI = {"startEvent", "endEvent", "userTask", "serviceTask", "exclusiveGateway", "parallelGateway",
              "sequenceFlow", "subProcess", "boundaryEvent", "intermediateCatchEvent", "callActivity"}
# elementi che non sono passi del flusso: il motore li legge senza problemi
IGNORATI = {"laneSet", "textAnnotation", "association", "documentation", "extensionElements", "ioSpecification",
            "dataStoreReference", "group", "category"}
PAROLE_EL = {"and", "or", "not", "eq", "ne", "lt", "gt", "le", "ge", "empty", "true", "false", "null", "div",
             "mod", "instanceof", "execution", "task"}
ESPRESSIONE = re.compile(r"\$\{(.*?)}", re.S)
IDENTIFICATORE = re.compile(r"[a-zA-Z_]\w*", re.A)
NOME = re.compile(r"[a-zA-Z][\w-]*", re.A)

# Catalogo dei connettori (connettore/ConnettoreOrditogram e implementazioni). Allinealo quando ne nasce uno.
ERRORE_STRADA, ERRORE_BLOCCA = "Segui la strada di errore", "Blocca e riprova più tardi"
CONNETTORI = {
    "apiConnector": [
        # nome, etichetta, tipo, obbligatorio, predefinito, opzioni
        ("metodo", "Metodo", "scelta", True, "GET", ["GET", "POST", "PUT", "PATCH", "DELETE"]),
        ("url", "Indirizzo", "testo", True, None, []),
        ("credenziale", "Credenziale", "credenziale", False, "", []),
        ("intestazioni", "Intestazioni", "codice", False, None, []),
        ("corpo", "Corpo della richiesta", "codice", False, None, []),
        ("risposta", "Dati da salvare dalla risposta", "mappatura", False, None, []),
        ("timeout", "Attesa massima (secondi)", "testo", False, "30", []),
        ("inErrore", "Se la chiamata non riesce", "scelta", True, ERRORE_STRADA, [ERRORE_STRADA, ERRORE_BLOCCA]),
    ],
    "notificaConnector": [
        ("destinatario", "A chi", "destinatario", True, "iniziatore", []),
        ("titolo", "Titolo", "testo", True, None, []),
        ("messaggio", "Messaggio", "testoLungo", False, None, []),
    ],
    "emailConnector": [
        ("destinatari", "A chi", "destinatariEmail", True, "iniziatore", []),
        ("oggetto", "Oggetto", "testo", True, None, []),
        ("testo", "Testo", "testoLungo", True, None, []),
        ("allegati", "Allegati", "scelta", True, "Nessuno", ["Nessuno", "Tutti i documenti della pratica"]),
    ],
    "greetingConnector": [],
}

TIPI_CAMPO = {"text", "number", "date", "textarea", "select", "checkbox", "tabella", "organizzazione", "anagrafica"}
TIPI_COLONNA = {"text", "number", "date", "select", "checkbox"}
TIPI_DOCUMENTI = {"carica", "lettura", "modifica"}
FORMATI = {"email", "codiceFiscale", "partitaIva", "cap", "telefono", "iban", "espressione"}
MAX_RIGHE = 500
CODICE_ANAGRAFICA = re.compile(r"[a-z][a-z0-9-]{1,59}")
CODICE_RUOLO = re.compile(r"[a-z0-9][a-z0-9-]{1,59}")
RUOLO_PARTE = re.compile(r"[a-zA-Z][a-zA-Z0-9_]{0,59}")


class Problemi:
    def __init__(self):
        self.errori, self.avvisi = [], []

    def errore(self, msg, el=None):
        self.errori.append((msg, el))

    def avviso(self, msg, el=None):
        self.avvisi.append((msg, el))


def q(ns, tag):
    return "{%s}%s" % (ns, tag)


def locale(el):
    return el.tag.split("}")[-1] if "}" in el.tag else el.tag


def piene(s):
    return s is not None and s.strip() != ""


def dati_usati(testo):
    out = []
    for m in ESPRESSIONE.finditer(testo or ""):
        expr = re.sub(r"'[^']*'|\"[^\"]*\"", " ", m.group(1))
        for i in IDENTIFICATORE.finditer(expr):
            if i.start() > 0 and expr[i.start() - 1] == ".":
                continue
            if i.group() not in PAROLE_EL and i.group() not in out:
                out.append(i.group())
    return out


# ------------------------------------------------------------------ form

def numero(v):
    if v is None:
        return None
    try:
        return float(str(v).strip())
    except ValueError:
        return None


def intero(v):
    if v is None:
        return None
    try:
        return int(str(v).strip())
    except ValueError:
        return None


def data(v):
    if v is None:
        return None
    s = str(v).strip()
    if s.lower() == "oggi":
        return date.today()
    try:
        return date.fromisoformat(s)
    except ValueError:
        return None


def problemi_regole(c, tipo):
    out = []
    if tipo == "number":
        mn, mx = numero(c.get("min")), numero(c.get("max"))
        if "min" in c and mn is None: out.append("il minimo deve essere un numero")
        if "max" in c and mx is None: out.append("il massimo deve essere un numero")
        if mn is not None and mx is not None and mn > mx: out.append("il minimo è più grande del massimo")
    if tipo == "date":
        mn, mx = data(c.get("min")), data(c.get("max"))
        if "min" in c and mn is None: out.append("la data minima deve essere AAAA-MM-GG oppure \"oggi\"")
        if "max" in c and mx is None: out.append("la data massima deve essere AAAA-MM-GG oppure \"oggi\"")
        if mn and mx and mn > mx: out.append("la data minima è dopo la massima")
    if tipo in ("text", "textarea"):
        mn, mx = intero(c.get("minLunghezza")), intero(c.get("maxLunghezza"))
        if "minLunghezza" in c and (mn is None or mn < 0): out.append("la lunghezza minima deve essere un numero intero")
        if "maxLunghezza" in c and (mx is None or mx < 1): out.append("la lunghezza massima deve essere un numero intero positivo")
        if mn is not None and mx is not None and mn > mx: out.append("la lunghezza minima è più grande della massima")
        formato = c.get("formato") or ""
        if formato and formato not in FORMATI: out.append("formato \"%s\" non previsto" % formato)
        if formato == "espressione":
            e = c.get("espressione") or ""
            if not e.strip(): out.append("scrivi l'espressione del formato")
            elif len(e) > 300: out.append("l'espressione è troppo lunga (al massimo 300 caratteri)")
            else:
                try:
                    re.compile(e)
                except re.error as ex:
                    out.append("l'espressione non è valida: %s" % ex)
    elif "formato" in c:
        out.append("il formato si usa solo con i campi di testo")
    return out


def sezione(f):
    return f.get("type") == "sezione"


def valida_form(chiave, testo, tipi_ammessi, anagrafiche_note, pb, per_anagrafica=False):
    """Come FormService.validate: il primo problema blocca (come il backend), qui li raccogliamo tutti."""
    pre = "Form \"%s\": " % chiave
    try:
        form = json.loads(testo)
    except json.JSONDecodeError as e:
        pb.errore(pre + "non è un JSON valido: %s" % e)
        return None
    if not isinstance(form, dict):
        pb.errore(pre + "deve essere un oggetto JSON"); return None
    fields = form.get("fields")
    if not isinstance(fields, list):
        pb.errore(pre + "deve avere un array \"fields\""); return None
    doc = form.get("documenti") or ""
    if doc and doc not in TIPI_DOCUMENTI: pb.errore(pre + "\"documenti\" deve essere: carica, lettura o modifica")
    vis = form.get("documentiVisibilita") or ""
    if vis and vis not in ("interna", "parti"): pb.errore(pre + "\"documentiVisibilita\" deve essere: interna o parti")
    nomi, parti, titoli = set(), set(), 0
    for f in fields:
        if sezione(f):
            if not (f.get("label") or "").strip(): pb.errore(pre + "c'è una sezione senza titolo")
            if "visibileSe" in f: pb.errore(pre + "una sezione non può avere una condizione")
            continue
        nome = f.get("name") or ""
        c = pre + "campo \"%s\": " % nome
        if not nome.strip(): pb.errore(pre + "c'è un campo senza nome"); continue
        if not NOME.fullmatch(nome): pb.errore(c + "il nome deve iniziare con una lettera e contenere solo lettere, numeri, - e _")
        if nome in nomi: pb.errore(pre + "il campo \"%s\" è duplicato" % nome)
        nomi.add(nome)
        tipo = f.get("type") or "text"
        if tipo not in tipi_ammessi: pb.errore(c + "tipo \"%s\" non supportato" % tipo); continue
        if tipo == "select" and not isinstance(f.get("options"), list): pb.errore(c + "un select deve avere \"options\"")
        for r in problemi_regole(f, tipo): pb.errore(c + r)
        if tipo == "tabella": valida_tabella(c, f, pb)
        if tipo == "anagrafica":
            ta = f.get("tipoAnagrafica") or ""
            if not CODICE_ANAGRAFICA.fullmatch(ta):
                pb.errore(c + "indica il codice dell'anagrafica da cui scegliere (tipoAnagrafica)")
            elif not per_anagrafica and anagrafiche_note is not None and ta not in anagrafiche_note:
                pb.errore(c + "l'anagrafica \"%s\" non esiste (aggiungila in anagrafiche/ o indicala con --anagrafiche-esistenti)" % ta)
        # accesso
        if "visibileAlleParti" in f and not isinstance(f["visibileAlleParti"], bool):
            pb.errore(c + "\"visibileAlleParti\" deve essere true o false")
        if "ruoliVisibili" in f:
            rv = f["ruoliVisibili"]
            if not isinstance(rv, list): pb.errore(c + "\"ruoliVisibili\" deve essere un elenco di codici di ruolo")
            elif any(not isinstance(x, str) or not CODICE_RUOLO.fullmatch(x) for x in rv):
                pb.errore(c + "in \"ruoliVisibili\" ci sono codici di ruolo non validi")
        if "parte" in f:
            if tipo != "organizzazione": pb.errore(c + "solo un campo Organizzazione può indicare una parte della pratica")
            elif not RUOLO_PARTE.fullmatch(str(f["parte"])): pb.errore(c + "il ruolo della parte deve iniziare con una lettera e contenere solo lettere, numeri e _")
            elif f["parte"] in parti: pb.errore(c + "la parte \"%s\" è già indicata da un altro campo" % f["parte"])
            parti.add(f.get("parte"))
        if f.get("titolo"):
            titoli += 1
            if tipo == "tabella": pb.errore(c + "una tabella non può fare da titolo della pratica")
        if f.get("readonly") and f.get("required"):
            pb.avviso(c + "un campo in sola lettura non si compila: \"required\" non ha effetto")
    for f in fields:
        if "visibileSe" not in f or sezione(f): continue
        nome = f.get("name") or ""
        c = pre + "campo \"%s\": " % nome
        cond = f.get("visibileSe")
        campo = cond.get("campo") if isinstance(cond, dict) else None
        valori = cond.get("valori") if isinstance(cond, dict) else None
        if isinstance(valori, str): valori = [valori]
        if not campo: pb.errore(c + "in \"visibileSe\" indica il \"campo\" da cui dipende"); continue
        if campo == nome: pb.errore(c + "la condizione non può dipendere dal campo stesso")
        elif campo not in nomi: pb.errore(c + "la condizione dipende da \"%s\", che non è nel form" % campo)
        if any(o.get("name") == campo and o.get("type") == "tabella" for o in fields):
            pb.errore(c + "la condizione non può dipendere da una tabella")
        if not valori or any(not str(v).strip() for v in valori):
            pb.errore(c + "la condizione deve indicare almeno un valore")
        else:
            dip = next((o for o in fields if o.get("name") == campo), None)
            if dip and dip.get("type") == "select" and isinstance(dip.get("options"), list):
                fuori = [v for v in valori if v not in dip["options"]]
                if fuori: pb.avviso(c + "la condizione usa valori che \"%s\" non offre: %s" % (campo, ", ".join(map(str, fuori))))
            if dip and dip.get("type") == "checkbox" and any(str(v) not in ("true", "false") for v in valori):
                pb.avviso(c + "per una casella la condizione usa i valori \"true\" o \"false\"")
    if not per_anagrafica:
        if titoli > 1: pb.avviso(pre + "più campi fanno da titolo: vale il primo")
        if doc == "carica" and form.get("documentiObbligatori") is None:
            pass
    return form


def valida_tabella(c, f, pb):
    col = f.get("colonne")
    if not isinstance(col, list) or not col: pb.errore(c + "una tabella deve avere almeno una colonna"); return
    if len(col) > 20: pb.errore(c + "una tabella può avere al massimo 20 colonne")
    nomi = set()
    for k in col:
        n = k.get("name") or ""
        if not NOME.fullmatch(n): pb.errore(c + "ogni colonna ha un nome che inizia con una lettera e contiene solo lettere, numeri, - e _"); continue
        if n in nomi: pb.errore(c + "la colonna \"%s\" è duplicata" % n)
        nomi.add(n)
        t = k.get("type") or "text"
        if t not in TIPI_COLONNA: pb.errore(c + "colonna \"%s\": tipo \"%s\" non ammesso in una tabella" % (n, t))
        if t == "select" and not k.get("options"): pb.errore(c + "colonna \"%s\": una scelta deve avere le opzioni" % n)
        for r in problemi_regole(k, t): pb.errore(c + "colonna \"%s\": %s" % (n, r))
    mn, mx = intero(f.get("minRighe")), intero(f.get("maxRighe"))
    if "minRighe" in f and (mn is None or mn < 0): pb.errore(c + "le righe minime devono essere un numero intero")
    if "maxRighe" in f and (mx is None or mx < 1 or mx > MAX_RIGHE): pb.errore(c + "le righe massime devono essere un numero da 1 a %d" % MAX_RIGHE)
    if mn is not None and mx is not None and mn > mx: pb.errore(c + "le righe minime sono più delle massime")


def tipi_campi(form):
    out = {}
    for f in (form or {}).get("fields", []):
        n = f.get("name")
        if not n: continue
        t = f.get("type")
        out[n] = "boolean" if t == "checkbox" else "json" if t == "tabella" else "string"
    return out


# ------------------------------------------------------------------ anagrafiche

def valida_anagrafica(path, codici_noti, pb):
    pre = "Anagrafica %s: " % path.name
    try:
        t = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        pb.errore(pre + "non è un JSON valido: %s" % e); return None
    codice = t.get("codice") or ""
    if not CODICE_ANAGRAFICA.fullmatch(codice):
        pb.errore(pre + "il codice deve essere in minuscolo, con numeri e trattini (es. \"fornitori\")"); return None
    if path.stem != codice: pb.avviso(pre + "chiama il file come il codice: %s.json" % codice)
    if not (t.get("nome") or "").strip(): pb.errore(pre + "manca il nome")
    d = t.get("definizione")
    if not isinstance(d, dict): pb.errore(pre + "manca la definizione dei campi (\"definizione\": {\"fields\": [...]})"); return None
    testo = json.dumps(d)
    if len(testo) > 200_000: pb.errore(pre + "la definizione è troppo grande")
    tipi = (TIPI_CAMPO - {"organizzazione"}) | {"anagrafica"}
    valida_form("anagrafica " + codice, testo, tipi, None, pb, per_anagrafica=True)
    campi = [f for f in d.get("fields", []) if not sezione(f)]
    if len(d.get("fields", [])) > 150: pb.errore(pre + "al massimo 150 campi")
    if not campi: pb.errore(pre + "serve almeno un campo")
    titoli = 0
    for f in campi:
        n, tipo = f.get("name", ""), f.get("type") or "text"
        c = pre + "campo \"%s\": " % n
        if len(n) > 100: pb.errore(c + "il nome è troppo lungo (al massimo 100 caratteri)")
        chiave, cerc = bool(f.get("chiave")), bool(f.get("cercabile"))
        riservato = isinstance(f.get("ruoliVisibili"), list) and f["ruoliVisibili"]
        if "parte" in f: pb.errore(c + "in un'anagrafica un campo non indica parti della pratica")
        if f.get("readonly") and f.get("required"): pb.errore(c + "un campo in sola lettura non può essere obbligatorio")
        if (chiave or cerc) and tipo not in ("text", "number", "date", "select"):
            pb.errore(c + "solo testo, numero, data o scelta possono essere chiave o ricercabili")
        if chiave and "visibileSe" in f: pb.errore(c + "un campo chiave non può essere condizionale")
        if (chiave or cerc or f.get("titolo")) and riservato:
            pb.errore(c + "un campo chiave, ricercabile o titolo non può essere riservato ad alcuni ruoli")
        if f.get("titolo"):
            titoli += 1
            if tipo not in ("text", "number", "date", "select"):
                pb.errore(c + "il titolo deve essere un campo di testo, numero, data o scelta")
        if tipo == "anagrafica":
            coll = f.get("tipoAnagrafica") or ""
            if not coll: pb.errore(c + "indica l'anagrafica collegata (tipoAnagrafica)")
            elif coll != codice and coll not in codici_noti: pb.errore(c + "l'anagrafica \"%s\" non esiste" % coll)
        elif "tipoAnagrafica" in f:
            pb.errore(c + "\"tipoAnagrafica\" si usa solo con i campi di tipo anagrafica")
    if titoli > 1: pb.errore(pre + "un solo campo può fare da titolo")
    if titoli == 0: pb.avviso(pre + "nessun campo fa da titolo: nelle ricerche le schede si vedranno senza nome")
    return t


# ------------------------------------------------------------------ permessi

def valida_permessi(path, avvio_bpmn, pb):
    pre = "permessi.json: "
    try:
        regole = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        pb.errore(pre + "non è un JSON valido: %s" % e); return
    if not isinstance(regole, list): pb.errore(pre + "deve essere un elenco di regole"); return
    avvia = set()
    for r in regole:
        az = (r.get("azione") or "").upper()
        ts = (r.get("tipoSoggetto") or "").upper()
        amb = (r.get("ambito") or "TUTTE").upper()
        sog = (r.get("soggetto") or "").strip()
        if az not in ("AVVIA", "VEDE", "ESPORTA"): pb.errore(pre + "azione sconosciuta: %s" % r.get("azione"))
        if ts not in ("GRUPPO", "RUOLO"): pb.errore(pre + "indica se la regola vale per un gruppo o per un ruolo")
        if amb not in ("TUTTE", "STRUTTURA", "STRUTTURA_E_SOTTO"): pb.errore(pre + "ambito sconosciuto: %s" % r.get("ambito"))
        if not sog: pb.errore(pre + "ogni regola indica il gruppo o il ruolo")
        if az != "VEDE" and amb != "TUTTE": pb.errore(pre + "l'ambito si usa solo con VEDE")
        if az == "AVVIA" and ts == "GRUPPO": avvia.add(sog)
        if az == "AVVIA" and ts == "RUOLO": avvia.add("ruolo:" + sog)
        if ts == "RUOLO" and sog not in ("amministratore", "analista", "gestore-utenti", "auditor"):
            pb.avviso(pre + "il ruolo \"%s\" non è tra i predefiniti: va creato nell'installazione, altrimenti la regola si tralascia" % sog)
    if avvia and avvio_bpmn and avvia != set(avvio_bpmn):
        pb.avviso(pre + "le regole AVVIA (%s) sono diverse da candidateStarterGroups del BPMN (%s): valgono le regole"
                  % (", ".join(sorted(avvia)), ", ".join(avvio_bpmn)))


def valida_permessi_anagrafiche(path, codici, pb):
    """permessi-anagrafiche.json: {"codice": [{"azione", "tipoSoggetto", "soggetto"}]} (TipiAnagraficaService.Regola)."""
    pre = "permessi-anagrafiche.json: "
    try:
        tutte = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        pb.errore(pre + "non è un JSON valido: %s" % e); return
    if not isinstance(tutte, dict): pb.errore(pre + "deve essere un oggetto {codice dell'anagrafica: [regole]}"); return
    for codice, regole in tutte.items():
        if codice not in codici: pb.errore(pre + "l'anagrafica \"%s\" non esiste" % codice)
        for r in regole if isinstance(regole, list) else []:
            if (r.get("azione") or "").upper() not in ("VEDE", "MODIFICA", "ESPORTA", "SCEGLIE"):
                pb.errore(pre + "%s: azione sconosciuta %s (VEDE, MODIFICA, ESPORTA, SCEGLIE)" % (codice, r.get("azione")))
            if (r.get("tipoSoggetto") or "").upper() not in ("GRUPPO", "RUOLO"):
                pb.errore(pre + "%s: indica se la regola vale per un gruppo o per un ruolo" % codice)
            if not (r.get("soggetto") or "").strip(): pb.errore(pre + "%s: ogni regola indica il gruppo o il ruolo" % codice)


# ------------------------------------------------------------------ BPMN

class Nodo:
    def __init__(self, el, contenitore):
        self.el, self.contenitore = el, contenitore
        self.id = el.get("id")
        self.tipo = locale(el)
        self.nome = el.get("name")
        self.entra, self.esce = [], []
        defs = [c for c in el if locale(c).endswith("EventDefinition")]
        self.defs = defs
        self.unica = locale(defs[0]) if len(defs) == 1 else None

    def descrivi(self):
        t = self.tipo
        u = self.unica
        if t == "startEvent": s = "L'inizio"
        elif t == "endEvent": s = "La fine"
        elif t == "userTask": s = "L'attività"
        elif t == "serviceTask": s = "L'attività automatica"
        elif t.endswith("Gateway"): s = "La decisione"
        elif t == "subProcess": s = "Il sottoprocesso"
        elif t == "callActivity": s = "Il processo richiamato"
        elif t == "boundaryEvent":
            if u == "errorEventDefinition": s = "La gestione errore"
            elif u == "messageEventDefinition": s = "Il messaggio sul bordo"
            elif self.el.get("cancelActivity", "true") != "false": s = "La scadenza"
            else: s = "Il sollecito"
        elif t == "intermediateCatchEvent" and u == "messageEventDefinition": s = "Il messaggio in arrivo"
        elif t == "intermediateCatchEvent": s = "L'attesa"
        else: s = "L'elemento"
        return '%s "%s"' % (s, self.nome) if piene(self.nome) else s + " senza nome"


def campi_connettore(el):
    out = {}
    ext = el.find(q(BPMN, "extensionElements"))
    if ext is None: return out
    for f in ext.findall(q(ACT, "field")):
        v = f.get("stringValue")
        if v is None: v = f.get("expression")
        if v is None:
            s = f.find(q(ACT, "string"))
            e = f.find(q(ACT, "expression"))
            v = (s.text if s is not None else e.text if e is not None else None)
        out[f.get("name")] = v
    return out


def analizza_bpmn(path, forms, pb, credenziali, processi_pubblicati):
    try:
        # il BPMN è un file della soluzione, letto in locale; expat non risolve entità esterne
        albero = ET.parse(path)  # nosemgrep: python.lang.security.use-defused-xml-parse.use-defused-xml-parse
    except ET.ParseError as e:
        pb.errore("Il BPMN non è un XML valido: %s" % e); return None
    radice = albero.getroot()
    if radice.tag != q(BPMN, "definitions"):
        pb.errore("Il file non è un BPMN 2.0 (manca <definitions> nel namespace BPMN)"); return None
    processi = radice.findall(q(BPMN, "process"))
    eseguibili = [p for p in processi if p.get("isExecutable", "true") != "false"]
    if len(eseguibili) != 1:
        pb.errore("Serve esattamente un processo eseguibile (isExecutable=\"true\"): trovati %d" % len(eseguibili)); return None
    p = eseguibili[0]
    if len(processi) > 1: pb.avviso("Il file contiene più processi: Orditogram usa solo quello eseguibile")
    chiave = p.get("id")
    if not chiave or not re.fullmatch(r"[A-Za-z_][\w.-]*", chiave, re.A):
        pb.errore("L'identificativo del processo deve iniziare con una lettera e contenere solo lettere, numeri, punto, - e _")
    if not piene(p.get("name")): pb.avviso("Il processo non ha un nome: gli utenti vedranno l'identificativo tecnico")

    messaggi = {m.get("id"): m.get("name") for m in radice.findall(q(BPMN, "message"))}
    errori_def = {e.get("id") for e in radice.findall(q(BPMN, "error"))}

    nodi, flussi, ids = {}, [], {}

    def visita(cont, nome_cont):
        for el in cont:
            t = locale(el)
            if el.tag.startswith("{%s}" % BPMN) is False or t in IGNORATI:
                continue
            i = el.get("id")
            if i:
                if i in ids: pb.errore("L'identificativo \"%s\" è usato da più elementi" % i, i)
                ids[i] = el
            if t not in SUPPORTATI:
                n = Nodo(el, nome_cont)
                pb.errore("%s: questo tipo di elemento (%s) non è ancora supportato da Orditogram" % (
                    ('L\'elemento "%s"' % n.nome) if piene(n.nome) else "Un elemento", t), i)
                continue
            if t == "sequenceFlow":
                flussi.append(el); continue
            nodi[i] = Nodo(el, nome_cont)
            if t == "subProcess": visita(el, i)

    visita(p, None)
    for f in flussi:
        s, d = f.get("sourceRef"), f.get("targetRef")
        if s not in nodi or d not in nodi:
            pb.errore("La freccia \"%s\" collega elementi che non esistono (%s → %s)" % (f.get("id"), s, d), f.get("id")); continue
        nodi[s].esce.append(f); nodi[d].entra.append(f)
        if nodi[s].contenitore != nodi[d].contenitore:
            pb.errore("La freccia \"%s\" attraversa il bordo di un sottoprocesso: collega il sottoprocesso, non i passi dentro" % f.get("id"), f.get("id"))

    # eventi
    for n in nodi.values():
        if n.tipo in ("startEvent", "endEvent") and n.defs:
            pb.errore(n.descrivi() + ": gli inizi e le fini con timer, messaggi o segnali non sono ancora supportati", n.id)
        if n.tipo == "intermediateCatchEvent" and n.unica not in ("timerEventDefinition", "messageEventDefinition"):
            pb.errore(n.descrivi() + ": nel flusso sono supportate le attese a tempo e i messaggi in arrivo", n.id)
        if n.tipo == "boundaryEvent":
            if n.unica not in ("timerEventDefinition", "errorEventDefinition", "messageEventDefinition"):
                pb.errore(n.descrivi() + ": sul bordo delle attività sono supportati scadenze, solleciti, errori e messaggi", n.id)
            att = nodi.get(n.el.get("attachedToRef"))
            if att is None:
                pb.errore(n.descrivi() + ": è attaccato a un elemento che non esiste", n.id)
            elif n.unica == "errorEventDefinition" and att.tipo not in ("serviceTask", "subProcess", "callActivity"):
                pb.avviso(n.descrivi() + ": gli errori arrivano solo dalle attività automatiche o dai sottoprocessi", n.id)
            if n.unica == "errorEventDefinition":
                ref = n.defs[0].get("errorRef")
                if ref and ref not in errori_def and ref != "ERRORE_API":
                    pb.avviso(n.descrivi() + ": l'errore \"%s\" non è dichiarato; senza errorRef prende tutti gli errori" % ref, n.id)
        if n.unica == "messageEventDefinition":
            ref = n.defs[0].get("messageRef")
            if not piene(ref):
                pb.errore(n.descrivi() + ": dai un nome al messaggio atteso", n.id)
            elif not NOME.fullmatch(ref):
                pb.errore(n.descrivi() + ": il nome del messaggio deve iniziare con una lettera e contenere solo lettere, numeri, - e _", n.id)
            elif ref in messaggi:
                nome = messaggi[ref]
                if nome is None or not NOME.fullmatch(nome):
                    pb.errore(n.descrivi() + ": il messaggio \"%s\" deve avere un nome senza spazi, che inizi con una lettera" % (nome or ref), n.id)
            else:
                pb.avviso(n.descrivi() + ": dichiara <message id=\"%s\" name=\"%s\"/> sotto <definitions>" % (ref, ref), n.id)
        if n.unica == "timerEventDefinition":
            td = n.defs[0]
            durata, dat, ciclo = (td.find(q(BPMN, x)) for x in ("timeDuration", "timeDate", "timeCycle"))
            vals = [x.text if x is not None else None for x in (durata, dat, ciclo)]
            if not any(piene(v) for v in vals):
                pb.errore(n.descrivi() + ": imposta dopo quanto tempo o a quale data deve scattare", n.id)
            cal = td.get(q(ACT, "businessCalendarName"))
            if piene(cal):
                if cal.strip() not in ("lavorativi", "proroga"):
                    pb.errore(n.descrivi() + ": il calendario \"%s\" non esiste (usa giorni lavorativi o proroga se festivo)" % cal, n.id)
                elif piene(vals[2]):
                    pb.errore(n.descrivi() + ": i giorni lavorativi non si usano con le ripetizioni: scegli \"dopo\" o \"alla data\"", n.id)
            if piene(vals[2]) and n.tipo == "boundaryEvent" and n.el.get("cancelActivity", "true") != "false":
                pb.avviso(n.descrivi() + ": una scadenza che interrompe l'attività scatta una volta sola, la ripetizione non serve", n.id)
            if piene(vals[0]) and "${" not in vals[0] and not re.fullmatch(r"P(?!$)(\d+Y)?(\d+M)?(\d+W)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)?", vals[0].strip()):
                pb.errore(n.descrivi() + ": la durata \"%s\" non è nel formato ISO 8601 (es. P30D, PT2H)" % vals[0], n.id)

    # inizio e fine
    inizi = [n for n in nodi.values() if n.tipo == "startEvent" and n.contenitore is None]
    if not inizi: pb.errore("Manca l'evento di inizio")
    if len(inizi) > 1:
        for s in inizi: pb.errore("C'è più di un evento di inizio: ne serve uno solo", s.id)
    if not any(n.tipo == "endEvent" and n.contenitore is None for n in nodi.values()):
        pb.errore("Manca l'evento di fine: la pratica non potrebbe concludersi")
    for sp in [n for n in nodi.values() if n.tipo == "subProcess"]:
        dentro = [n for n in nodi.values() if n.contenitore == sp.id]
        if sum(1 for n in dentro if n.tipo == "startEvent") != 1:
            pb.errore(sp.descrivi() + ": dentro il sottoprocesso serve esattamente un inizio", sp.id)
        if not any(n.tipo == "endEvent" for n in dentro):
            pb.errore(sp.descrivi() + ": dentro il sottoprocesso serve almeno una fine", sp.id)

    # collegamenti
    for n in nodi.values():
        if n.tipo == "boundaryEvent":
            if not n.esce: pb.errore(n.descrivi() + " non ha frecce in uscita: collega il passo da fare quando scatta", n.id)
        elif n.tipo == "startEvent":
            if not n.esce: pb.errore(n.descrivi() + " non è collegato a nessun passo", n.id)
        elif n.tipo == "endEvent":
            if not n.entra: pb.errore(n.descrivi() + " non è raggiungibile: nessuna freccia arriva qui", n.id)
        else:
            if not n.entra: pb.errore(n.descrivi() + " non è raggiungibile: nessuna freccia arriva qui", n.id)
            if not n.esce: pb.errore(n.descrivi() + " non ha frecce in uscita: la pratica si fermerebbe qui", n.id)

    # form e gruppi
    form_keys, gruppi = [], set()
    for s in inizi:
        fk = s.el.get(q(ACT, "formKey"))
        if piene(fk): form_keys.append(fk)
        else: pb.avviso(s.descrivi() + ": senza form di avvio la pratica non ha dati né titolo", s.id)
    compiti = [n for n in nodi.values() if n.tipo == "userTask"]
    for t in compiti:
        fk = t.el.get(q(ACT, "formKey"))
        if piene(fk): form_keys.append(fk)
        else: pb.avviso(t.descrivi() + " non ha un form: l'utente vedrà solo il pulsante Completa", t.id)
        cg = [g.strip() for g in (t.el.get(q(ACT, "candidateGroups")) or "").split(",") if g.strip()]
        cu = [u.strip() for u in (t.el.get(q(ACT, "candidateUsers")) or "").split(",") if u.strip()]
        gruppi.update(g for g in cg if not g.startswith("parte:"))
        if not cg and not cu and not piene(t.el.get(q(ACT, "assignee"))):
            pb.errore(t.descrivi() + ": scegli il gruppo che deve lavorare questa attività", t.id)
        t.gruppi = cg
    definizioni = {}
    for fk in dict.fromkeys(form_keys):
        if fk in forms: definizioni[fk] = forms[fk]
        else:
            el = next((n.id for n in list(inizi) + compiti if n.el.get(q(ACT, "formKey")) == fk), None)
            pb.errore("Il form \"%s\" non esiste: crealo o scegline un altro" % fk, el)
    for fk in forms:
        if fk not in definizioni: pb.avviso("Il form \"%s\" non è usato dal processo: non finirà nel pacchetto" % fk)

    parti = {"richiedente"}
    for d in definizioni.values():
        for f in d.get("fields", []):
            if f.get("parte"): parti.add(f["parte"])
    for t in compiti:
        for g in t.gruppi:
            if g.startswith("parte:") and g[6:] not in parti:
                pb.errore(t.descrivi() + ": è assegnata alla parte \"%s\", ma nessun form la indica. Aggiungi un campo Organizzazione "
                          "che sia questa parte, oppure usa \"richiedente\"" % g[6:], t.id)

    # dati disponibili
    dati = set(DATI_DI_SISTEMA)
    for d in definizioni.values(): dati.update(tipi_campi(d))
    for n in nodi.values():
        if n.tipo == "serviceTask" and n.el.get("implementation") == "apiConnector":
            for riga in (campi_connettore(n.el).get("risposta") or "").splitlines():
                if "=" in riga:
                    nome, val = riga.split("=", 1)
                    if val.strip() and NOME.fullmatch(nome.strip()): dati.add(nome.strip())
        if n.tipo == "serviceTask" and n.el.get("implementation") == "greetingConnector":
            dati.add("saluto")
        if n.unica == "messageEventDefinition":
            ext = n.el.find(q(BPMN, "extensionElements"))
            if ext is not None:
                for e in ext.findall(q(ORD, "datiMessaggio")):
                    for x in re.split(r"[,\s]+", e.text or ""):
                        if NOME.fullmatch(x): dati.add(x)
    chiamate = [n for n in nodi.values() if n.tipo == "callActivity"]
    if chiamate:
        pb.avviso("Ci sono processi richiamati: i loro dati non sono controllati qui, li controlla Orditogram alla pubblicazione")
    for n in nodi.values():
        if n.unica == "timerEventDefinition":
            for x in ("timeDate", "timeDuration", "timeCycle"):
                e = n.defs[0].find(q(BPMN, x))
                for d in dati_usati(e.text if e is not None else ""):
                    if d not in dati and not chiamate:
                        pb.avviso(n.descrivi() + ": usa il dato \"%s\" che nessun form raccoglie" % d, n.id)

    # connettori
    for n in [x for x in nodi.values() if x.tipo == "serviceTask"]:
        impl = n.el.get("implementation")
        if not piene(impl) or impl == "##WebService":
            if piene(n.el.get(q(ACT, "class"))) or piene(n.el.get(q(ACT, "delegateExpression"))):
                pb.errore(n.descrivi() + ": usa implementation=\"<connettore>\" (activiti:class e delegateExpression non sono ammessi)", n.id)
            else:
                pb.errore(n.descrivi() + ": scegli cosa deve fare questa attività automatica", n.id)
            continue
        if impl.startswith("${"): continue
        if impl not in CONNETTORI:
            pb.errore(n.descrivi() + ": il connettore \"%s\" non esiste in questa installazione" % impl, n.id); continue
        valori = campi_connettore(n.el)
        for (nome, etich, tipo, obbl, pred, opz) in CONNETTORI[impl]:
            v = valori.get(nome)
            vuoto = not piene(v)
            if obbl and vuoto and not piene(pred): pb.errore(n.descrivi() + ": manca \"%s\"" % etich, n.id)
            if not vuoto and tipo == "scelta" and opz and v not in opz:
                pb.errore(n.descrivi() + ": \"%s\" ha un valore non previsto (%s)" % (etich, v), n.id)
            if not vuoto and tipo == "credenziale" and v.strip() not in credenziali:
                pb.avviso(n.descrivi() + ": la credenziale \"%s\" non è configurata su questo server" % v, n.id)
            if v:
                for d in dati_usati(v):
                    if d not in dati and not chiamate:
                        pb.avviso(n.descrivi() + ": \"%s\" usa il dato \"%s\" che nessun form raccoglie" % (etich, d), n.id)
            if not vuoto and tipo in ("destinatario", "destinatariEmail"):
                for r in [x.strip() for x in v.split(",") if x.strip()]:
                    if r.startswith("campo:") and r[6:] not in dati:
                        pb.avviso(n.descrivi() + ": \"A chi\" usa il campo \"%s\" che nessun form raccoglie" % r[6:], n.id)
                    if r.startswith("gruppo:") and r[7:] not in gruppi:
                        pb.avviso(n.descrivi() + ": il gruppo \"%s\" non lavora nessuna attività del processo: controlla il nome" % r[7:], n.id)
        if impl == "apiConnector" and valori.get("inErrore") != ERRORE_BLOCCA and not ha_gestione_errore(n, nodi):
            pb.avviso(n.descrivi() + ": se non riesce la pratica si ferma. Aggiungi \"Se non riesce\" per indicare la strada da seguire", n.id)

    # processi richiamati
    for c in chiamate:
        ch = (c.el.get("calledElement") or "").strip()
        if not ch: pb.errore(c.descrivi() + ": scegli il processo da richiamare", c.id); continue
        if ch.startswith("${"): pb.errore(c.descrivi() + ": indica il processo da richiamare per nome, non con un'espressione", c.id); continue
        if ch == chiave: pb.errore(c.descrivi() + ": un processo non può richiamare sé stesso", c.id); continue
        if ch not in processi_pubblicati:
            pb.avviso(c.descrivi() + ": il processo \"%s\" deve essere già pubblicato quando si pubblica questo" % ch, c.id)
        if not ha_gestione_errore(c, nodi):
            pb.avviso(c.descrivi() + ": se il processo richiamato si ferma per un errore, si ferma anche questa pratica. "
                      "Puoi aggiungere \"Se non riesce\" per indicare la strada da seguire", c.id)

    # decisioni
    for g in [x for x in nodi.values() if x.tipo == "exclusiveGateway"]:
        if len(g.esce) < 2: continue
        pred = g.el.get("default")
        senza = [f for f in g.esce if not piene(testo_condizione(f)) and f.get("id") != pred]
        if senza:
            pb.errore(g.descrivi() + ": ogni freccia in uscita deve avere una condizione, oppure essere la freccia \"altrimenti\"", g.id)
        elif not piene(pred):
            pb.avviso(g.descrivi() + ": se nessuna condizione è vera la pratica si blocca. Conviene indicare una freccia \"altrimenti\"", g.id)
    for g in [x for x in nodi.values() if x.tipo == "parallelGateway"]:
        if any(piene(testo_condizione(f)) for f in g.esce):
            pb.avviso(g.descrivi() + ": le condizioni sulle frecce di un parallelo sono ignorate", g.id)
    for f in flussi:
        cond = testo_condizione(f)
        if cond is None: continue
        for d in dati_usati(cond):
            if d not in dati and not chiamate:
                pb.avviso("La condizione \"%s\" usa il dato \"%s\" che nessun form raccoglie" % (f.get("name") or f.get("id"), d), f.get("id"))
        confronta_opzioni(f, cond, definizioni, pb)

    # disegno
    piano = radice.find(".//" + q("http://www.omg.org/spec/BPMN/20100524/DI", "BPMNPlane"))
    if piano is None:
        pb.errore("Il BPMN non ha il disegno (BPMNDiagram): lancia script/disposizione.py, altrimenti lo Studio mostra una tela vuota")
    else:
        disegnati = {s.get("bpmnElement") for s in radice.iter() if locale(s) in ("BPMNShape", "BPMNEdge")}
        mancanti = [i for i in list(nodi) + [f.get("id") for f in flussi] if i not in disegnati]
        if mancanti:
            pb.avviso("Questi elementi non hanno il disegno: %s (rilancia script/disposizione.py)" % ", ".join(mancanti[:10]))

    return {"chiave": chiave, "nome": p.get("name"), "form": list(definizioni), "gruppi": sorted(gruppi),
            "compiti": [(t.id, t.nome, t.gruppi) for t in compiti],
            "avvio": [g.strip() for g in (p.get(q(ACT, "candidateStarterGroups")) or "").split(",") if g.strip()]}


def testo_condizione(f):
    c = f.find(q(BPMN, "conditionExpression"))
    return c.text if c is not None else None


def confronta_opzioni(f, cond, definizioni, pb):
    """campo == 'Valore' con un valore che il select non offre: la strada non verrebbe mai presa."""
    opzioni = {}
    for d in definizioni.values():
        for x in d.get("fields", []):
            if x.get("type") == "select" and isinstance(x.get("options"), list):
                opzioni[x["name"]] = x["options"]
    for m in re.finditer(r"([a-zA-Z_]\w*)\s*(?:==|eq|!=|ne)\s*'([^']*)'", cond):
        campo, valore = m.group(1), m.group(2)
        if campo in opzioni and valore not in opzioni[campo]:
            pb.errore("La condizione \"%s\" confronta \"%s\" con '%s', che non è tra le scelte del campo (%s)" % (
                f.get("name") or f.get("id"), campo, valore, ", ".join(opzioni[campo])), f.get("id"))


def ha_gestione_errore(n, nodi):
    el = n
    while el is not None:
        if any(b.tipo == "boundaryEvent" and b.el.get("attachedToRef") == el.id and b.unica == "errorEventDefinition"
               for b in nodi.values()):
            return True
        el = nodi.get(el.contenitore) if el.contenitore else None
    return False


# ------------------------------------------------------------------ scenari di collaudo

def valida_scenari(path, info, forms, pb):
    pre = "collaudo/scenari.json: "
    try:
        s = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        pb.errore(pre + "non è un JSON valido: %s" % e); return
    nomi_compiti = {t[1] for t in info["compiti"]} | {t[0] for t in info["compiti"]}
    for i, sc in enumerate(s.get("scenari", [])):
        nome = sc.get("nome") or "scenario %d" % (i + 1)
        if not sc.get("passi"): pb.errore(pre + "\"%s\" non ha passi" % nome); continue
        if sc["passi"][0].get("azione") != "avvia": pb.errore(pre + "\"%s\" deve iniziare con il passo \"avvia\"" % nome)
        for p in sc["passi"]:
            az = p.get("azione")
            if az not in ("avvia", "completa", "messaggio", "timer", "controlla"):
                pb.errore(pre + "\"%s\": azione \"%s\" sconosciuta (avvia, completa, messaggio, timer, controlla)" % (nome, az))
            if az == "completa" and p.get("attivita") not in nomi_compiti:
                pb.errore(pre + "\"%s\": l'attività \"%s\" non è nel processo" % (nome, p.get("attivita")))
    if not s.get("scenari"): pb.avviso(pre + "nessuno scenario: il collaudo automatico non ha niente da provare")


# ------------------------------------------------------------------ main

def main(argv):
    import argparse
    ap = argparse.ArgumentParser(description="Verifica una soluzione Orditogram")
    ap.add_argument("cartella")
    ap.add_argument("--credenziali", default="protocollo-demo", help="credenziali configurate sul server di destinazione")
    ap.add_argument("--anagrafiche-esistenti", default="", help="tipi di anagrafica già presenti nell'installazione")
    ap.add_argument("--processi-pubblicati", default="", help="processi già pubblicati (per i processi richiamati)")
    ap.add_argument("--json", action="store_true", help="stampa il risultato in JSON")
    a = ap.parse_args(argv)
    base = Path(a.cartella)
    pb = Problemi()

    bpmn = [x for x in list(base.glob("*.bpmn20.xml")) + list(base.glob("*.bpmn"))]
    if len(bpmn) != 1:
        pb.errore("Nella cartella serve un solo file .bpmn20.xml (trovati %d)" % len(bpmn))

    # anagrafiche prima dei form
    tipi_file = sorted((base / "anagrafiche").glob("*.json")) if (base / "anagrafiche").is_dir() else []
    codici = {json.loads(p.read_text(encoding="utf-8")).get("codice") for p in tipi_file if p.read_text(encoding="utf-8").strip().startswith("{")}
    codici |= {x for x in a.anagrafiche_esistenti.split(",") if x}
    for p in tipi_file: valida_anagrafica(p, codici, pb)

    forms = {}
    for p in sorted((base / "form").glob("*.json")) if (base / "form").is_dir() else []:
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,200}", p.stem):
            pb.errore("Form %s: il nome del file (formKey) usa solo lettere, numeri, - e _" % p.name); continue
        f = valida_form(p.stem, p.read_text(encoding="utf-8"), TIPI_CAMPO, codici, pb)
        if f is not None: forms[p.stem] = f

    info = None
    if len(bpmn) == 1:
        info = analizza_bpmn(bpmn[0], forms, pb, {x for x in a.credenziali.split(",") if x},
                             {x for x in a.processi_pubblicati.split(",") if x})
    if (base / "permessi.json").exists(): valida_permessi(base / "permessi.json", info["avvio"] if info else [], pb)
    if (base / "permessi-anagrafiche.json").exists():
        valida_permessi_anagrafiche(base / "permessi-anagrafiche.json", codici, pb)
    elif tipi_file:
        pb.avviso("Ci sono anagrafiche ma non permessi-anagrafiche.json: le schede le vedrà solo l'amministratore")
    if info and (base / "collaudo" / "scenari.json").exists(): valida_scenari(base / "collaudo" / "scenari.json", info, forms, pb)

    if a.json:
        print(json.dumps({"processo": info, "errori": [m for m, _ in pb.errori], "avvisi": [m for m, _ in pb.avvisi]},
                         ensure_ascii=False, indent=2))
    else:
        if info:
            print("Processo: %s (%s)" % (info["nome"], info["chiave"]))
            print("Form: %s" % (", ".join(info["form"]) or "nessuno"))
            print("Gruppi che lavorano: %s" % (", ".join(info["gruppi"]) or "nessuno"))
            print("Chi avvia (BPMN): %s" % (", ".join(info["avvio"]) or "tutti"))
        print()
        for m, el in pb.errori: print("ERRORE  %s%s" % (m, "  [%s]" % el if el else ""))
        for m, el in pb.avvisi: print("AVVISO  %s%s" % (m, "  [%s]" % el if el else ""))
        print("\n%d errori, %d avvisi. %s" % (len(pb.errori), len(pb.avvisi),
              "Pronto da pubblicare." if not pb.errori else "Da correggere prima di pubblicare."))
    return 1 if pb.errori else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
