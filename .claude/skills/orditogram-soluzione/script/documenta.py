#!/usr/bin/env python3
"""
Scrive la scheda tecnica della soluzione, ricavata dagli artefatti (non a mano: così è sempre esatta).

    python3 documenta.py <cartella-soluzione> [-o documentazione/scheda-tecnica.md]

Contiene: passi del processo con chi li lavora, form e scadenze; decisioni e condizioni; scadenze, solleciti e
attese in parole; attività automatiche (servizi chiamati, email, notifiche); messaggi in arrivo con l'indirizzo
dell'API; tutti i form campo per campo (tipo, obbligatorio, regole, condizioni, chi lo vede); anagrafiche; permessi;
gruppi, credenziali e configurazioni che il cliente deve predisporre.
È la base fattuale per i documenti scritti (funzionale, manuale utente, manuale d'esercizio, piano di test):
vedi riferimenti/documentazione.md.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import verifica  # noqa: E402
from verifica import BPMN, ACT, ORD, q, locale, campi_connettore  # noqa: E402

TIPI = {"text": "Testo", "textarea": "Testo lungo", "number": "Numero", "date": "Data", "select": "Scelta",
        "checkbox": "Sì/No", "tabella": "Tabella", "organizzazione": "Organizzazione", "anagrafica": "Anagrafica"}
FORMATI = {"email": "email", "codiceFiscale": "codice fiscale", "partitaIva": "partita IVA", "cap": "CAP",
           "telefono": "telefono", "iban": "IBAN", "espressione": "formato personalizzato"}
DOC = {"carica": "si caricano documenti", "lettura": "documenti in sola lettura", "modifica": "documenti modificabili"}


def durata(iso):
    """P30D → 30 giorni; PT2H → 2 ore; R3/P7D → ogni 7 giorni, al massimo 3 volte."""
    if not iso: return ""
    s = iso.strip()
    if "${" in s: return "dalla data o durata nel dato %s" % ", ".join(verifica.dati_usati(s))
    m = re.fullmatch(r"R(\d*)/(.+)", s)
    if m:
        return "ogni %s%s" % (durata(m.group(2)), ", al massimo %s volte" % m.group(1) if m.group(1) else "")
    m = re.fullmatch(r"P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?", s)
    if not m: return s
    nomi = [("anno", "anni"), ("mese", "mesi"), ("settimana", "settimane"), ("giorno", "giorni"), ("ora", "ore"),
            ("minuto", "minuti"), ("secondo", "secondi")]
    parti = ["%s %s" % (v, nomi[i][0] if v == "1" else nomi[i][1]) for i, v in enumerate(m.groups()) if v]
    return " e ".join(parti) or s


def calendario(c):
    return {"lavorativi": " (giorni lavorativi)", "proroga": " (se scade in un giorno festivo, slitta al primo lavorativo)"}.get(c or "", "")


def cella(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


def main(argv):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("cartella")
    ap.add_argument("-o", "--uscita")
    a = ap.parse_args(argv)
    base = Path(a.cartella)
    bpmn = next(iter(list(base.glob("*.bpmn20.xml")) + list(base.glob("*.bpmn"))))
    forms = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted((base / "form").glob("*.json"))}
    anagrafiche = [json.loads(p.read_text(encoding="utf-8")) for p in sorted((base / "anagrafiche").glob("*.json"))] \
        if (base / "anagrafiche").is_dir() else []
    permessi = json.loads((base / "permessi.json").read_text(encoding="utf-8")) if (base / "permessi.json").exists() else []
    info = verifica.analizza_bpmn(bpmn, forms, verifica.Problemi(), set(), set())
    # il BPMN è un file della soluzione, letto in locale; expat non risolve entità esterne
    radice = ET.parse(bpmn).getroot()  # nosemgrep: python.lang.security.use-defused-xml-parse.use-defused-xml-parse
    p = next(x for x in radice.findall(q(BPMN, "process")) if x.get("isExecutable", "true") != "false")
    doc = p.find(q(BPMN, "documentation"))
    messaggi = {m.get("id"): m.get("name") for m in radice.findall(q(BPMN, "message"))}

    nodi = {}
    for el in p.iter():
        if el.get("id") and locale(el) not in ("sequenceFlow",) and el is not p: nodi[el.get("id")] = el
    flussi = list(p.iter(q(BPMN, "sequenceFlow")))
    nome = lambda i: (nodi[i].get("name") if i in nodi and nodi[i].get("name") else i)

    r = ["# Scheda tecnica: %s" % (info["nome"] or info["chiave"]), "",
         "Generata dagli artefatti della soluzione con `documenta.py`: non modificarla a mano, rigenerala.", ""]
    if doc is not None and doc.text: r += [doc.text.strip(), ""]
    r += ["| | |", "|---|---|",
          "| Identificativo del processo | `%s` |" % info["chiave"],
          "| Chi può avviare (BPMN) | %s |" % (", ".join(info["avvio"]) or "tutti gli utenti"),
          "| Gruppi che lavorano le attività | %s |" % (", ".join(info["gruppi"]) or "nessuno"),
          "| Form | %s |" % ", ".join("`%s`" % f for f in info["form"]),
          "| Anagrafiche | %s |" % (", ".join("`%s`" % t["codice"] for t in anagrafiche) or "nessuna"), ""]
    if (base / "documentazione" / "diagramma.png").exists() or (base / "diagramma.png").exists():
        r += ["![Diagramma del processo](diagramma.png)", ""]

    # --- passi
    r += ["## Passi del processo", "", "| Passo | Tipo | Chi | Form | Note |", "|---|---|---|---|---|"]
    bordi = {}
    for el in nodi.values():
        if locale(el) == "boundaryEvent": bordi.setdefault(el.get("attachedToRef"), []).append(el)
    for el in p.iter():
        t = locale(el)
        if t not in ("startEvent", "userTask", "serviceTask", "subProcess", "callActivity", "intermediateCatchEvent", "endEvent"):
            continue
        dentro = "" if el in list(p) else " (nel sottoprocesso)"
        chi, form, note = "", el.get(q(ACT, "formKey")) or "", []
        if t == "userTask":
            g = el.get(q(ACT, "candidateGroups")) or ""
            chi = ", ".join(("la parte " + x[6:]) if x.startswith("parte:") else "gruppo " + x for x in g.split(",") if x.strip())
            if el.get(q(ACT, "assignee")): chi = "utente " + el.get(q(ACT, "assignee"))
            tipo = "Attività"
        elif t == "serviceTask":
            tipo, chi = "Automatica", "Orditogram"
            note.append({"apiConnector": "chiama un servizio esterno", "emailConnector": "invia un'email",
                         "notificaConnector": "invia una notifica"}.get(el.get("implementation"), el.get("implementation")))
        elif t == "startEvent":
            if el not in list(p): continue
            tipo, chi = "Avvio", ", ".join(info["avvio"]) or "tutti"
        elif t == "endEvent":
            tipo = "Fine"
        elif t == "subProcess":
            tipo = "Sottoprocesso"
        elif t == "callActivity":
            tipo = "Processo richiamato"; note.append("avvia il processo `%s`" % el.get("calledElement"))
        else:
            d = [c for c in el if locale(c).endswith("EventDefinition")]
            if d and locale(d[0]) == "timerEventDefinition":
                tipo = "Attesa"; note.append("aspetta " + durata(testo_timer(d[0])) + calendario(d[0].get(q(ACT, "businessCalendarName"))))
            else:
                tipo = "Messaggio in arrivo"; note.append("aspetta il messaggio `%s`" % messaggi.get(d[0].get("messageRef"), d[0].get("messageRef")))
        for b in bordi.get(el.get("id"), []):
            note.append(descrivi_bordo(b, messaggi, nome, flussi))
        r.append("| %s%s | %s | %s | %s | %s |" % (cella(el.get("name") or el.get("id")), dentro, tipo, cella(chi),
                                                  "`%s`" % form if form else "", cella("; ".join(n for n in note if n))))
    r.append("")

    # --- decisioni
    gw = [el for el in nodi.values() if locale(el) in ("exclusiveGateway", "parallelGateway")]
    if gw:
        r += ["## Decisioni", ""]
        for g in gw:
            uscite = [f for f in flussi if f.get("sourceRef") == g.get("id")]
            if locale(g) == "parallelGateway":
                r.append("- **%s** (in parallelo): %s" % (g.get("name") or g.get("id"),
                         ", ".join(nome(f.get("targetRef")) for f in uscite) if len(uscite) > 1 else "riunisce le strade parallele"))
                continue
            r.append("- **%s**" % (g.get("name") or g.get("id")))
            for f in uscite:
                c = f.find(q(BPMN, "conditionExpression"))
                cond = "altrimenti" if f.get("id") == g.get("default") else ("`%s`" % c.text.strip() if c is not None and c.text else "sempre")
                r.append("  - %s → %s%s" % (cond, nome(f.get("targetRef")), " (\"%s\")" % f.get("name") if f.get("name") else ""))
        r.append("")

    # --- servizi esterni e comunicazioni
    st = [el for el in nodi.values() if locale(el) == "serviceTask"]
    if st:
        r += ["## Attività automatiche", ""]
        for el in st:
            v = campi_connettore(el)
            impl = el.get("implementation")
            r.append("### %s" % (el.get("name") or el.get("id")))
            r.append("")
            if impl == "apiConnector":
                r += ["- Chiamata **%s** a `%s`" % (v.get("metodo", "GET"), v.get("url")),
                      "- Credenziale: %s" % ("`%s` (configurata sul server)" % v["credenziale"] if v.get("credenziale") else "nessuna"),
                      "- Dati salvati dalla risposta: %s" % (", ".join("`%s`" % x.strip() for x in (v.get("risposta") or "").splitlines() if x.strip()) or "nessuno"),
                      "- Attesa massima: %s secondi" % v.get("timeout", "30"),
                      "- Se non riesce: %s" % (v.get("inErrore") or verifica.ERRORE_STRADA)]
                if v.get("corpo"): r += ["- Corpo della richiesta:", "", "```json", v["corpo"].strip(), "```"]
            elif impl == "emailConnector":
                r += ["- A: %s" % destinatari(v.get("destinatari", "iniziatore")),
                      "- Oggetto: %s" % v.get("oggetto", ""), "- Allegati: %s" % v.get("allegati", "Nessuno"),
                      "- Testo:", "", "```", (v.get("testo") or "").strip(), "```"]
            elif impl == "notificaConnector":
                r += ["- A: %s" % destinatari(v.get("destinatario", "iniziatore")), "- Titolo: %s" % v.get("titolo", ""),
                      "- Messaggio: %s" % cella(v.get("messaggio", ""))]
            else:
                r.append("- Connettore `%s`" % impl)
            r.append("")

    # --- messaggi in arrivo
    ev_msg = [el for el in nodi.values() if any(locale(c) == "messageEventDefinition" for c in el)]
    if ev_msg:
        r += ["## Messaggi in arrivo da altri sistemi", "",
              "| Messaggio | Dove | Dati portati | Indirizzo |", "|---|---|---|---|"]
        for el in ev_msg:
            ref = next(c for c in el if locale(c) == "messageEventDefinition").get("messageRef")
            n = messaggi.get(ref, ref)
            ext = el.find(q(BPMN, "extensionElements"))
            dati = [e.text for e in ext.findall(q(ORD, "datiMessaggio"))] if ext is not None else []
            dove = "sul bordo di \"%s\"" % nome(el.get("attachedToRef")) if el.get("attachedToRef") else "nel flusso"
            r.append("| `%s` | %s | %s | `POST /api/messaggi/%s` (sistemi, chiave API) o `POST /api/pratiche/{id}/messaggi/%s` (operatore) |"
                     % (n, dove, ", ".join(dati) or "-", n, n))
        r.append("")

    # --- form
    r += ["## Form", ""]
    for fk in info["form"]:
        f = forms[fk]
        r += ["### %s (`%s`)" % (f.get("title") or fk, fk), ""]
        if f.get("description"): r += [f["description"], ""]
        usato = [nome(i) for i, el in nodi.items() if el.get(q(ACT, "formKey")) == fk]
        extra = ["usato in: " + ", ".join(usato)]
        if f.get("documenti"): extra.append(DOC[f["documenti"]] + (" (almeno uno obbligatorio)" if f.get("documentiObbligatori") else ""))
        if f.get("documentiVisibilita") == "parti": extra.append("i documenti li vedono anche le parti esterne")
        r += ["_" + "; ".join(extra) + "_", "", "| Campo | Nome | Tipo | Obbl. | Regole e note |", "|---|---|---|---|---|"]
        r += righe_campi(f.get("fields", []))
        r.append("")

    # --- anagrafiche
    if anagrafiche:
        r += ["## Anagrafiche", ""]
        for t in anagrafiche:
            r += ["### %s (`%s`)" % (t.get("nome"), t["codice"]), ""]
            if t.get("descrizione"): r += [t["descrizione"], ""]
            r += ["| Campo | Nome | Tipo | Obbl. | Regole e note |", "|---|---|---|---|---|"]
            r += righe_campi(t["definizione"].get("fields", []))
            r.append("")

    pa = json.loads((base / "permessi-anagrafiche.json").read_text(encoding="utf-8")) if (base / "permessi-anagrafiche.json").exists() else {}
    if pa:
        r += ["### Chi usa le anagrafiche", "", "| Anagrafica | Azione | Chi |", "|---|---|---|"]
        spiega = {"VEDE": "vede le schede", "MODIFICA": "crea e modifica le schede", "ESPORTA": "esporta in CSV",
                  "SCEGLIE": "sceglie una scheda nei form (solo nome)"}
        for codice, regole in pa.items():
            for x in regole:
                r.append("| `%s` | %s | %s %s |" % (codice, spiega.get(x["azione"].upper(), x["azione"]), x["tipoSoggetto"].lower(), x["soggetto"]))
        r.append("")

    # --- permessi
    r += ["## Chi avvia, vede ed esporta le pratiche", ""]
    if permessi:
        r += ["| Azione | Chi | Ambito |", "|---|---|---|"]
        for x in permessi:
            r.append("| %s | %s %s | %s |" % (x["azione"], x["tipoSoggetto"].lower(), x["soggetto"],
                                             {"TUTTE": "tutte le pratiche", "STRUTTURA": "pratiche della sua struttura",
                                              "STRUTTURA_E_SOTTO": "pratiche della sua struttura e delle strutture sotto"}.get(x.get("ambito") or "TUTTE")))
    else:
        r.append("Nessuna regola nello Studio: avvia chi è nei gruppi di avvio del BPMN; vede la pratica chi l'ha avviata, "
                 "chi ne lavora un'attività e chi ha il permesso di vedere tutte le pratiche.")
    r.append("")

    # --- da predisporre
    cred = sorted({campi_connettore(el).get("credenziale") for el in st if campi_connettore(el).get("credenziale")})
    email = any(el.get("implementation") == "emailConnector" for el in st)
    r += ["## Da predisporre nell'installazione del cliente", "",
          "- Gruppi (locali o dal provider di identità): %s" % (", ".join("`%s`" % g for g in sorted(set(info["gruppi"]) | set(info["avvio"]))) or "nessuno")]
    if cred: r.append("- Credenziali dei servizi esterni (`app.credenziali` o variabili d'ambiente): %s" % ", ".join("`%s`" % c for c in cred))
    if email: r.append("- Server email (`spring.mail.host`): senza, le email restano \"non inviate\" e la pratica prosegue")
    if ev_msg: r.append("- Chiave API con permesso `messaggi:invia` per ogni sistema che invia messaggi")
    ruoli = sorted({r_ for fk in info["form"] for c in forms[fk].get("fields", []) for r_ in c.get("ruoliVisibili", [])}
                   | {x["soggetto"] for x in permessi if x.get("tipoSoggetto", "").upper() == "RUOLO"})
    if ruoli: r.append("- Ruoli applicativi: %s" % ", ".join("`%s`" % x for x in ruoli))
    if any("${" in (testo_timer(d) or "") for el in nodi.values() for d in el if locale(d) == "timerEventDefinition"):
        r.append("- I timer che leggono una data dai dati della pratica richiedono che il dato sia sempre compilato")
    r.append("- Calendario delle festività aggiornato (Amministrazione → Calendario) se il processo usa giorni lavorativi o proroghe")
    r.append("")

    out = Path(a.uscita) if a.uscita else base / "documentazione" / "scheda-tecnica.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(r), encoding="utf-8")
    print("Scheda tecnica scritta in %s" % out)
    return 0


def testo_timer(d):
    for x in ("timeDuration", "timeDate", "timeCycle"):
        e = d.find(q(BPMN, x))
        if e is not None and e.text: return e.text
    return ""


def descrivi_bordo(b, messaggi, nome, flussi):
    d = next((c for c in b if locale(c).endswith("EventDefinition")), None)
    dopo = ", ".join(nome(f.get("targetRef")) for f in flussi if f.get("sourceRef") == b.get("id"))
    if d is None: return ""
    t = locale(d)
    if t == "timerEventDefinition":
        interrompe = b.get("cancelActivity", "true") != "false"
        return "%s \"%s\": %s%s → %s" % ("scadenza" if interrompe else "sollecito", b.get("name") or b.get("id"),
                                        durata(testo_timer(d)), calendario(d.get(q(ACT, "businessCalendarName"))), dopo)
    if t == "errorEventDefinition":
        return "se non riesce (\"%s\") → %s" % (b.get("name") or "errore", dopo)
    if t == "messageEventDefinition":
        return "se arriva il messaggio `%s` → %s" % (messaggi.get(d.get("messageRef"), d.get("messageRef")), dopo)
    return ""


def destinatari(v):
    out = []
    for x in [y.strip() for y in v.split(",") if y.strip()]:
        if x == "iniziatore": out.append("chi ha avviato la pratica")
        elif x.startswith("gruppo:"): out.append("il gruppo " + x[7:])
        elif x.startswith("campo:"): out.append("l'indirizzo nel campo `%s`" % x[6:])
        elif x.startswith("utente:"): out.append("l'utente " + x[7:])
        elif x.startswith("email:"): out.append(x[6:])
        else: out.append(x)
    return ", ".join(out)


def righe_campi(fields):
    out = []
    for c in fields:
        if c.get("type") == "sezione":
            out.append("| **%s** | | sezione | | %s |" % (cella(c.get("label")), cella(c.get("help", ""))))
            continue
        t = c.get("type") or "text"
        note = []
        if c.get("readonly"): note.append("sola lettura")
        if c.get("titolo"): note.append("titolo della pratica/scheda")
        if t == "select": note.append("scelte: " + ", ".join(c.get("options", [])))
        if t == "anagrafica": note.append("scheda di `%s`%s" % (c.get("tipoAnagrafica"), ", si può creare dal form" if c.get("creaScheda") else ""))
        if t == "organizzazione" and c.get("parte"): note.append("diventa la parte `%s` della pratica" % c["parte"])
        if t == "tabella":
            note.append("colonne: " + ", ".join("%s (%s)" % (k.get("label") or k.get("name"), TIPI.get(k.get("type") or "text")) for k in c.get("colonne", [])))
            if c.get("minRighe") is not None or c.get("maxRighe") is not None:
                note.append("righe da %s a %s" % (c.get("minRighe", 0), c.get("maxRighe", 500)))
        for k, s in (("min", "minimo %s"), ("max", "massimo %s"), ("minLunghezza", "almeno %s caratteri"), ("maxLunghezza", "al massimo %s caratteri")):
            if k in c: note.append(s % c[k])
        if c.get("formato"): note.append("formato " + FORMATI.get(c["formato"], c["formato"]))
        if c.get("visibileSe"):
            v = c["visibileSe"]
            note.append("solo se `%s` è %s" % (v.get("campo"), " o ".join("\"%s\"" % x for x in (v.get("valori") if isinstance(v.get("valori"), list) else [v.get("valori")]))))
        if c.get("chiave"): note.append("chiave (unica)")
        if c.get("cercabile"): note.append("ricercabile")
        if c.get("visibileAlleParti"): note.append("visibile alle parti esterne")
        if c.get("ruoliVisibili"): note.append("riservato ai ruoli " + ", ".join(c["ruoliVisibili"]))
        if c.get("help"): note.append(c["help"])
        out.append("| %s | `%s` | %s | %s | %s |" % (cella(c.get("label")), c.get("name"), TIPI.get(t, t),
                                                    "sì" if c.get("required") else "", cella("; ".join(note))))
    return out


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
