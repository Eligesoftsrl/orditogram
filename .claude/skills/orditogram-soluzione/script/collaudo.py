#!/usr/bin/env python3
"""
Collaudo automatico di una soluzione su un Orditogram avviato (sviluppo o collaudo, MAI produzione).

    python3 collaudo.py <cartella-soluzione> [--url http://localhost:8080] [--admin admin:admin]
                        [--pubblica] [--db "postgresql://orditogram:orditogram@localhost/orditogram"]

Con --pubblica importa il pacchetto (crea anagrafiche, form, bozza e permessi) e pubblica il processo.
Poi crea gli utenti di prova e le schede di esempio indicati in collaudo/scenari.json e percorre ogni scenario
come farebbero le persone: avvia la pratica, completa le attività con gli utenti dei gruppi giusti, invia i messaggi,
fa scattare subito i timer (serve --db: anticipa la scadenza nella tabella dei timer di Activiti) e controlla
attività correnti, dati, eventi dello storico e fine raggiunta.

Alla fine scrive collaudo/report.md e collaudo/report.json: esito di ogni scenario, passi, tempi e la copertura
del diagramma (quali passi nessuno scenario ha percorso). Esce con 1 se uno scenario fallisce.

Formato di collaudo/scenari.json: vedi riferimenti/collaudo.md.
"""
import base64
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import verifica  # noqa: E402

ATTESA = 25          # secondi massimi per vedere l'effetto di un passo (connettori, timer)


class Fallito(Exception):
    pass


class Client:
    def __init__(self, url, password_predefinita):
        self.url = url.rstrip("/")
        self.password = {}
        self.predefinita = password_predefinita

    def chiama(self, metodo, percorso, utente, corpo=None, file=None, ok=(200, 201)):
        intest = {"Authorization": "Basic " + base64.b64encode(
            ("%s:%s" % (utente, self.password.get(utente, self.predefinita))).encode()).decode()}
        dati = None
        if file is not None:
            confine = uuid.uuid4().hex
            parti = []
            for nome, valore in (corpo or {}).items():
                parti.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (confine, nome, valore)).encode())
            for nome, (nomefile, contenuto) in file:
                parti.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n"
                              "Content-Type: application/octet-stream\r\n\r\n" % (confine, nome, nomefile)).encode() + contenuto + b"\r\n")
            parti.append(("--%s--\r\n" % confine).encode())
            dati = b"".join(parti)
            intest["Content-Type"] = "multipart/form-data; boundary=" + confine
        elif corpo is not None:
            dati = json.dumps(corpo).encode()
            intest["Content-Type"] = "application/json"
        req = urllib.request.Request(self.url + percorso, data=dati, headers=intest, method=metodo)
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                testo = r.read().decode() or "null"
                return json.loads(testo) if testo.strip()[:1] in "[{n" else testo
        except urllib.error.HTTPError as e:
            testo = e.read().decode()
            try:
                j = json.loads(testo)
                msg = j.get("message") or testo
                if j.get("campi"): msg += ": " + "; ".join("%s %s" % (c.get("campo"), c.get("messaggio")) for c in j["campi"])
            except (ValueError, AttributeError):
                msg = testo
            if e.code in ok: return None
            raise Fallito("%s %s come %s: HTTP %d %s" % (metodo, percorso, utente, e.code, msg[:400]))


def sostituisci(valore, schede):
    """"@scheda:tipo:indice" diventa l'id della scheda creata (come testo, come lo invia l'interfaccia)."""
    if isinstance(valore, str):
        m = re.fullmatch(r"@scheda:([a-z][a-z0-9-]*):(\d+)", valore)
        if m:
            try:
                return str(schede[m.group(1)][int(m.group(2))])
            except (KeyError, IndexError):
                raise Fallito("la scheda %s non è stata creata" % valore)
        return valore
    if isinstance(valore, list): return [sostituisci(v, schede) for v in valore]
    if isinstance(valore, dict): return {k: sostituisci(v, schede) for k, v in valore.items()}
    return valore


def dati_pratica(valore, cli, admin, pi):
    """"@dato:nome" diventa il valore attuale di quel dato della pratica (es. un codice generato da un servizio)."""
    if isinstance(valore, dict) and any(isinstance(v, str) and v.startswith("@dato:") for v in valore.values()):
        letti = {x["nome"]: x["value"] for x in cli.chiama("GET", "/api/pratiche/" + pi, admin).get("dati", [])}
        out = {}
        for k, v in valore.items():
            if isinstance(v, str) and v.startswith("@dato:"):
                if v[6:] not in letti: raise Fallito("la pratica non ha il dato \"%s\"" % v[6:])
                v = letti[v[6:]]
            out[k] = v
        return out
    return valore


def attendi(cond, descrizione):
    fine = time.time() + ATTESA
    ultimo = None
    while time.time() < fine:
        ultimo = cond()
        if ultimo: return ultimo
        time.sleep(0.7)
    raise Fallito("dopo %d secondi: %s" % (ATTESA, descrizione))


def main(argv):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("cartella")
    ap.add_argument("--url", default="http://localhost:8080")
    ap.add_argument("--admin", default="admin:admin")
    ap.add_argument("--pubblica", action="store_true")
    ap.add_argument("--db", help="connessione PostgreSQL per far scattare i timer (solo ambienti di prova)")
    ap.add_argument("--solo", help="esegue solo gli scenari il cui nome contiene questo testo")
    a = ap.parse_args(argv)
    base = Path(a.cartella)
    conf = json.loads((base / "collaudo" / "scenari.json").read_text(encoding="utf-8"))
    admin, pw_admin = a.admin.split(":", 1)
    cli = Client(a.url, conf.get("password", "password"))
    cli.password[admin] = pw_admin

    bpmn = next(iter(list(base.glob("*.bpmn20.xml")) + list(base.glob("*.bpmn"))))
    forms = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted((base / "form").glob("*.json"))}
    info = verifica.analizza_bpmn(bpmn, forms, verifica.Problemi(), set(), set())
    chiave = info["chiave"]
    nomi_compiti = {t[0]: t[1] for t in info["compiti"]}
    gruppi_compito = {t[1]: t[2] for t in info["compiti"]}
    gruppi_compito.update({t[0]: t[2] for t in info["compiti"]})

    # --- pubblicazione
    if a.pubblica:
        import installa
        if installa.installa(cli, admin, base, bpmn, chiave) != 0: return 1

    # --- utenti di prova: un utente per gruppo, se non indicato
    utenti = dict(conf.get("utenti", {}))
    esistenti = {u["username"]: u for u in cli.chiama("GET", "/api/admin/users", admin)}
    for gruppo in sorted(set(info["gruppi"]) | set(info["avvio"]) | set(conf.get("gruppiExtra", []))):
        if gruppo in utenti: continue
        nome = "collaudo-" + re.sub(r"[^a-z0-9-]", "-", gruppo.lower())
        if nome not in esistenti:
            cli.chiama("POST", "/api/admin/users", admin, {
                "username": nome, "firstName": "Collaudo", "lastName": gruppo, "email": nome + "@collaudo.invalid",
                "enabled": True, "groups": [gruppo], "ruoli": [], "organizzazioni": [], "password": cli.predefinita,
                "tipo": "INTERNO"})
            print("Creato l'utente di prova %s (gruppo %s)" % (nome, gruppo))
        utenti[gruppo] = nome
    avviatore = utenti.get("avvio") or (utenti.get(info["avvio"][0]) if info["avvio"] else admin)

    # --- schede di esempio
    schede = {}
    for tipo, elenco in conf.get("schede", {}).items():
        schede[tipo] = []
        chiavi = []
        fdef = base / "anagrafiche" / (tipo + ".json")
        if fdef.exists():
            chiavi = [c["name"] for c in json.loads(fdef.read_text(encoding="utf-8"))["definizione"]["fields"] if c.get("chiave")]
        for dati in elenco:
            dati = sostituisci(dati, schede)
            try:
                r = cli.chiama("POST", "/api/anagrafiche/%s/schede" % tipo, admin, {"dati": dati})
            except Fallito as e:
                # già creata da un collaudo precedente: si riusa quella con la stessa chiave
                k = next((c for c in chiavi if dati.get(c)), None)
                if "già presente" not in str(e) or not k: raise
                trovate = cli.chiama("GET", "/api/anagrafiche/%s/schede?campo=%s&valore=%s" % (
                    tipo, k, urllib.parse.quote(str(dati[k]))), admin)["schede"]
                if not trovate: raise
                r = trovate[0]
            schede[tipo].append(r["id"])
        print("Schede di prova %s: %s" % (tipo, schede[tipo]))

    # --- scenari
    risultati, percorsi = [], set()
    for sc in conf.get("scenari", []):
        if a.solo and a.solo.lower() not in sc["nome"].lower(): continue
        inizio, passi, pi, esito, errore = time.time(), [], None, "superato", None
        variante = sc.get("variante")   # {"testo nel BPMN": "testo nuovo"}: si pubblica una versione modificata
        try:
            if variante:
                xml = bpmn.read_text(encoding="utf-8")
                for da, a_ in variante.items():
                    if da not in xml: raise Fallito("variante: \"%s\" non è nel BPMN" % da)
                    xml = xml.replace(da, a_)
                cli.chiama("POST", "/api/admin/processes/deploy", admin, {}, [("bpmn", (bpmn.name, xml.encode()))])
            for p in sc["passi"]:
                az = p["azione"]
                t0 = time.time()
                if az == "avvia":
                    u = p.get("utente", avviatore)
                    dati = sostituisci(p.get("dati", {}), schede)
                    allegati = [(f, (base / "collaudo" / f).read_bytes()) for f in p.get("allegati", [])]
                    form_avvio = forms.get(info["form"][0]) if info["form"] else None
                    if not allegati and form_avvio and form_avvio.get("documenti") == "carica" and form_avvio.get("documentiObbligatori"):
                        allegati = [("documento-di-prova.txt", b"Documento di prova del collaudo automatico")]
                    if allegati:
                        r = cli.chiama("POST", "/api/processes/%s/start" % chiave, u, {"data": json.dumps(dati)},
                                       [("files", x) for x in allegati])
                    else:
                        r = cli.chiama("POST", "/api/processes/%s/start" % chiave, u, dati)
                    pi = r["id"]
                    desc = "%s avvia la pratica \"%s\"" % (u, r.get("name"))
                elif az == "completa":
                    nome = nomi_compiti.get(p["attivita"], p["attivita"])
                    gr = [g for g in gruppi_compito.get(nome, []) if not g.startswith("parte:")]
                    u = p.get("utente") or (utenti.get(gr[0]) if gr else None)
                    if not u: raise Fallito("indica l'utente che completa \"%s\"" % nome)
                    task = attendi(lambda: next((t for t in cli.chiama("GET", "/api/tasks", u)
                                                 if t["processInstanceId"] == pi and t["name"] == nome), None),
                                   "%s non vede l'attività \"%s\" nella sua scrivania" % (u, nome))
                    cli.chiama("POST", "/api/tasks/%s/complete" % task["id"], u,
                               dati_pratica(sostituisci(p.get("dati", {}), schede), cli, admin, pi))
                    desc = "%s completa \"%s\"" % (u, nome)
                elif az == "messaggio":
                    u = p.get("utente", avviatore)
                    attendi(lambda: any(m["nome"] == p["nome"] for m in cli.chiama("GET", "/api/pratiche/" + pi, admin)["messaggiAttesi"]),
                            "la pratica non attende il messaggio \"%s\"" % p["nome"])
                    cli.chiama("POST", "/api/pratiche/%s/messaggi/%s" % (pi, p["nome"]), u, p.get("dati", {}))
                    desc = "arriva il messaggio \"%s\"" % p["nome"]
                elif az == "timer":
                    if not a.db: raise Fallito("per far scattare il timer \"%s\" serve --db" % p["elemento"])
                    sql = ("UPDATE act_ru_timer_job SET duedate_ = now() - interval '1 second' "
                           "WHERE process_instance_id_ = '%s' AND handler_cfg_ LIKE '%%\"activityId\":\"%s\"%%'"
                           % (pi.replace("'", ""), re.sub(r"[^\w.-]", "", p["elemento"])))
                    out = attendi(lambda: (lambda o: o if o.strip().endswith(("1", "2", "3")) else None)(
                        # psql con argomenti in lista (niente shell); nel SQL solo id ripuliti
                        subprocess.run(["psql", a.db, "-At", "-c", sql], capture_output=True, text=True).stdout),  # nosemgrep
                        "nessun timer \"%s\" in attesa su questa pratica" % p["elemento"])
                    time.sleep(1)
                    desc = "scatta subito il timer \"%s\"" % p["elemento"]
                elif az == "controlla":
                    desc = controlla(cli, admin, pi, p, utenti)
                else:
                    raise Fallito("azione sconosciuta: %s" % az)
                passi.append({"passo": desc, "secondi": round(time.time() - t0, 1), "esito": "ok"})
            if sc.get("atteso"):
                passi.append({"passo": controlla(cli, admin, pi, sc["atteso"], utenti), "esito": "ok"})
        except Fallito as e:
            esito, errore = "fallito", str(e)
            passi.append({"passo": str(e), "esito": "errore"})
        if variante:   # torna alla versione vera, per gli scenari dopo e per chi prova a mano
            cli.chiama("POST", "/api/admin/processes/deploy", admin, {}, [("bpmn", (bpmn.name, bpmn.read_bytes()))])
        if pi:
            try:
                d = cli.chiama("GET", "/api/pratiche/%s/diagramma" % pi, admin)
                percorsi.update(d.get("completedIds", [])); percorsi.update(d.get("activeIds", []))
            except Fallito:
                pass
        risultati.append({"scenario": sc["nome"], "descrizione": sc.get("descrizione", ""), "esito": esito,
                          "errore": errore, "pratica": pi, "secondi": round(time.time() - inizio, 1), "passi": passi})
        print("%s  %s%s" % ("OK     " if esito == "superato" else "FALLITO", sc["nome"], "" if not errore else ": " + errore))

    # --- copertura del diagramma
    tutti = elementi(bpmn)
    mai = [(i, n) for i, n in tutti if i not in percorsi]
    scrivi_report(base, info, risultati, tutti, mai, a.url)
    ok = sum(1 for r in risultati if r["esito"] == "superato")
    print("\n%d scenari su %d superati. Copertura: %d passi su %d percorsi." % (ok, len(risultati), len(tutti) - len(mai), len(tutti)))
    if mai: print("Mai percorsi: " + ", ".join("%s (%s)" % (n or i, i) for i, n in mai))
    print("Report: %s" % (base / "collaudo" / "report.md"))
    return 0 if ok == len(risultati) else 1


def controlla(cli, admin, pi, p, utenti=None):
    utenti = utenti or {}
    cosa = []

    def verifica_ora():
        d = cli.chiama("GET", "/api/pratiche/" + pi, admin)
        correnti = [s["nome"] for s in d.get("stepCorrenti", [])]
        if "attivita" in p:
            att = p["attivita"] if isinstance(p["attivita"], list) else [p["attivita"]]
            if sorted(att) != sorted(correnti): return None
        if p.get("conclusa") is True and d.get("stato") != "conclusa": return None
        if p.get("conclusa") is False and d.get("stato") == "conclusa": return None
        if "fine" in p:
            diag = cli.chiama("GET", "/api/pratiche/%s/diagramma" % pi, admin)
            if p["fine"] not in diag.get("completedIds", []): return None
        if "passa" in p:
            diag = cli.chiama("GET", "/api/pratiche/%s/diagramma" % pi, admin)
            if any(x not in diag.get("completedIds", []) + diag.get("activeIds", []) for x in p["passa"]): return None
        if "dati" in p:
            valori = {x["nome"]: x["value"] for x in d.get("dati", [])}
            for k, v in p["dati"].items():
                if v == "*":
                    if not valori.get(k): return None
                elif str(valori.get(k)) != str(v): return None
        if "eventi" in p:
            tipi = [e["tipo"] for e in d.get("eventi", [])]
            if any(t not in tipi for t in p["eventi"]): return None
        for chiave_, atteso in (("visibileA", True), ("nonVisibileA", False)):
            for u in p.get(chiave_, []):
                u = utenti.get(u[7:], u) if u.startswith("gruppo:") else u
                try:
                    cli.chiama("GET", "/api/pratiche/" + pi, u)
                    vede = True
                except Fallito:
                    vede = False
                if vede != atteso: return None
        return d

    def spiega():
        d = cli.chiama("GET", "/api/pratiche/" + pi, admin)
        diag = cli.chiama("GET", "/api/pratiche/%s/diagramma" % pi, admin)
        return ("atteso %s; la pratica è \"%s\", attività correnti: %s, passi fatti: %s, dati: %s, eventi: %s" % (
            json.dumps(p, ensure_ascii=False), d.get("stato"), [s["nome"] for s in d.get("stepCorrenti", [])],
            diag.get("completedIds"), {x["nome"]: x["value"] for x in d.get("dati", [])},
            [e["tipo"] for e in d.get("eventi", [])]))
    try:
        attendi(verifica_ora, "controllo non superato")
    except Fallito:
        raise Fallito(spiega())
    for k in ("attivita", "conclusa", "fine", "passa", "dati", "eventi", "visibileA", "nonVisibileA"):
        if k in p: cosa.append("%s %s" % (k, json.dumps(p[k], ensure_ascii=False)))
    return "controllo: " + "; ".join(cosa)


def elementi(bpmn):
    """Passi del diagramma da coprire: attività, decisioni, eventi (non le frecce)."""
    import xml.etree.ElementTree as ET
    out = []
    # il BPMN è un file della soluzione, letto in locale; expat non risolve entità esterne
    for el in ET.parse(bpmn).getroot().iter():  # nosemgrep: python.lang.security.use-defused-xml-parse.use-defused-xml-parse
        t = el.tag.split("}")[-1]
        if t in ("userTask", "serviceTask", "exclusiveGateway", "parallelGateway", "endEvent", "boundaryEvent",
                 "intermediateCatchEvent", "callActivity", "subProcess"):
            out.append((el.get("id"), el.get("name")))
    return out


def scrivi_report(base, info, risultati, tutti, mai, url):
    ok = sum(1 for r in risultati if r["esito"] == "superato")
    righe = ["# Report di collaudo: %s" % (info["nome"] or info["chiave"]), "",
             "Eseguito il %s su %s." % (datetime.now().strftime("%d/%m/%Y alle %H:%M"), url), "",
             "**%d scenari su %d superati.** Copertura del diagramma: %d passi su %d." % (
                 ok, len(risultati), len(tutti) - len(mai), len(tutti)), "",
             "| Scenario | Esito | Durata | Pratica |", "|---|---|---|---|"]
    for r in risultati:
        righe.append("| %s | %s | %s s | `%s` |" % (r["scenario"], "✅ superato" if r["esito"] == "superato" else "❌ fallito",
                                                 r["secondi"], r["pratica"] or "-"))
    for r in risultati:
        righe += ["", "## %s" % r["scenario"], ""]
        if r["descrizione"]: righe += [r["descrizione"], ""]
        for i, p in enumerate(r["passi"], 1):
            righe.append("%d. %s %s" % (i, "✅" if p["esito"] == "ok" else "❌", p["passo"]))
    righe += ["", "## Copertura", ""]
    if mai:
        righe.append("Passi che nessuno scenario ha percorso (da provare a mano o con uno scenario in più):")
        righe += ["- %s (`%s`)" % (n or "senza nome", i) for i, n in mai]
    else:
        righe.append("Tutti i passi del diagramma sono stati percorsi almeno una volta.")
    (base / "collaudo" / "report.md").write_text("\n".join(righe) + "\n", encoding="utf-8")
    (base / "collaudo" / "report.json").write_text(json.dumps(
        {"processo": info["chiave"], "risultati": risultati, "copertura": {"totale": len(tutti), "maiPercorsi": [i for i, _ in mai]}},
        ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Fallito as e:
        print("Collaudo interrotto: %s" % e)
        sys.exit(1)
