#!/usr/bin/env python3
"""Riepilogo dei rapporti di analisi del codice, per la CI.

Uso: riepilogo.py <strumento> <rapporto>
  strumento: semgrep | spotbugs | osv | npm | sonar

Stampa una tabella leggibile (anche nel riepilogo della CI, se c'è GITHUB_STEP_SUMMARY)
e conta i problemi "bloccanti" secondo le soglie qui sotto. Se ANALISI_BLOCCANTE=true
e ci sono problemi bloccanti, esce con codice 1 e la CI diventa rossa.

Soglie bloccanti:
  semgrep   regole con gravità ERROR
  spotbugs  categoria SECURITY con priorità alta, oppure rango 1-4 ("più preoccupanti")
  osv       vulnerabilità note con punteggio CVSS 7 o più (alta o critica)
  npm       vulnerabilità alte o critiche nelle librerie usate in produzione
  sonar     quality gate non superato
"""
import json
import os
import sys
import xml.etree.ElementTree as ET


def semgrep(percorso):
    dati = json.load(open(percorso, encoding="utf-8"))
    righe, bloccanti = [], 0
    for r in dati.get("results", []):
        gravita = r.get("extra", {}).get("severity", "")
        if gravita == "ERROR":
            bloccanti += 1
        messaggio = r.get("extra", {}).get("message", "").strip().splitlines()[0][:140]
        righe.append((gravita, r.get("check_id", "").split(".")[-1], f'{r.get("path")}:{r.get("start", {}).get("line")}', messaggio))
    righe.sort(key=lambda x: ["ERROR", "WARNING", "INFO"].index(x[0]) if x[0] in ("ERROR", "WARNING", "INFO") else 9)
    return ("Gravità", "Regola", "Dove", "Messaggio"), righe, bloccanti


def spotbugs(percorso):
    # il rapporto XML lo produce la nostra build, non arriva da fuori
    radice = ET.parse(percorso).getroot()  # nosemgrep: python.lang.security.use-defused-xml-parse.use-defused-xml-parse
    righe, bloccanti = [], 0
    for b in radice.iter("BugInstance"):
        categoria, priorita, rango = b.get("category"), int(b.get("priority", 3)), int(b.get("rank", 20))
        if (categoria == "SECURITY" and priorita == 1) or rango <= 4:
            bloccanti += 1
        riga = b.find("SourceLine")
        dove = f'{riga.get("sourcepath")}:{riga.get("start")}' if riga is not None else ""
        breve = b.findtext("ShortMessage") or b.get("type")
        righe.append((categoria, b.get("type"), f"p{priorita} r{rango}", dove, breve[:120]))
    righe.sort(key=lambda x: (x[0] != "SECURITY", x[2]))
    return ("Categoria", "Tipo", "Priorità/rango", "Dove", "Messaggio"), righe, bloccanti


def osv(percorso):
    dati = json.load(open(percorso, encoding="utf-8"))
    righe, bloccanti = [], 0
    for risultato in dati.get("results", []):
        origine = os.path.basename(risultato.get("source", {}).get("path", ""))
        for p in risultato.get("packages", []):
            pacchetto = p.get("package", {})
            riassunti = {v["id"]: v.get("summary", "") for v in p.get("vulnerabilities", [])}
            for g in p.get("groups", []):
                try:
                    punteggio = float(g.get("max_severity") or 0)
                except ValueError:
                    punteggio = 0.0
                if punteggio >= 7:
                    bloccanti += 1
                ids = g.get("ids", [])
                righe.append((f"{punteggio:.1f}", f'{pacchetto.get("name")} {pacchetto.get("version")}', origine,
                              ", ".join(ids[:2]), (riassunti.get(ids[0], "") if ids else "")[:110]))
    righe.sort(key=lambda x: -float(x[0]))
    return ("CVSS", "Libreria", "Da", "Avvisi", "Descrizione"), righe, bloccanti


def npm(percorso):
    dati = json.load(open(percorso, encoding="utf-8"))
    ordine = ["critical", "high", "moderate", "low", "info"]
    righe, bloccanti = [], 0
    for nome, v in dati.get("vulnerabilities", {}).items():
        gravita = v.get("severity", "")
        if gravita in ("critical", "high"):
            bloccanti += 1
        avvisi = [x.get("title", "") for x in v.get("via", []) if isinstance(x, dict)]
        correzione = v.get("fixAvailable")
        if isinstance(correzione, dict):
            correzione = f'{correzione.get("name")} {correzione.get("version")}' + (" (cambia versione maggiore)" if correzione.get("isSemVerMajor") else "")
        righe.append((gravita, nome, v.get("range", ""), (avvisi[0] if avvisi else "tramite " + ", ".join(map(str, v.get("via", []))))[:100], str(correzione)))
    righe.sort(key=lambda x: ordine.index(x[0]) if x[0] in ordine else 9)
    return ("Gravità", "Pacchetto", "Versioni", "Avviso", "Correzione"), righe, bloccanti


def sonar(percorso):
    dati = json.load(open(percorso, encoding="utf-8"))
    stato = dati.get("qualityGate", {}).get("projectStatus", {})
    righe = [("Quality gate", stato.get("status", "?"), "")]
    for c in stato.get("conditions", []):
        righe.append(("Condizione", f'{c.get("metricKey")}: {c.get("actualValue")}', f'{c.get("status")} (soglia {c.get("errorThreshold")})'))
    for f in dati.get("issues", {}).get("facets", []):
        for v in f.get("values", []):
            righe.append((f.get("property"), v.get("val"), str(v.get("count"))))
    righe.append(("Security hotspot da rivedere", str(dati.get("hotspots", {}).get("paging", {}).get("total", "?")), ""))
    for i in dati.get("issues", {}).get("issues", [])[:500]:
        impatti = ", ".join(f'{x.get("softwareQuality")}/{x.get("severity")}' for x in i.get("impacts", []))
        righe.append((impatti, i.get("rule", ""), f'{i.get("component", "").split(":", 1)[-1]}:{i.get("line", "")} {i.get("message", "")[:110]}'))
    bloccanti = 0 if stato.get("status") == "OK" else 1
    return ("Voce", "Valore", "Dettaglio"), righe, bloccanti


STRUMENTI = {"semgrep": semgrep, "spotbugs": spotbugs, "osv": osv, "npm": npm, "sonar": sonar}


def tabella(intestazione, righe):
    pulisci = lambda s: str(s).replace("|", "\\|").replace("\n", " ")
    out = ["| " + " | ".join(intestazione) + " |", "|" + "---|" * len(intestazione)]
    out += ["| " + " | ".join(pulisci(c) for c in r) + " |" for r in righe]
    return "\n".join(out)


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in STRUMENTI:
        sys.exit(__doc__)
    strumento, percorso = sys.argv[1], sys.argv[2]
    if not os.path.exists(percorso):
        testo = f"### {strumento}\n\nRapporto non trovato: `{percorso}`\n"
        bloccanti = 1
    else:
        intestazione, righe, bloccanti = STRUMENTI[strumento](percorso)
        testo = (f"### {strumento}: {len(righe)} segnalazioni, {bloccanti} bloccanti\n\n"
                 + (tabella(intestazione, righe[:400]) if righe else "Nessuna segnalazione.") + "\n")
    print(testo)
    riepilogo = os.environ.get("GITHUB_STEP_SUMMARY")
    if riepilogo:
        with open(riepilogo, "a", encoding="utf-8") as f:
            f.write(testo + "\n")
    if bloccanti and os.environ.get("ANALISI_BLOCCANTE", "true") == "true":
        sys.exit(1)


if __name__ == "__main__":
    main()
