#!/usr/bin/env python3
"""
Installa una soluzione su un Orditogram avviato, con le API dell'amministrazione (come farebbe l'analista dallo Studio).

    python3 installa.py <cartella-soluzione> --url https://orditogram.cliente.it --admin utente:password [--non-pubblicare]

1. crea il pacchetto (pacchetto.py, che prima verifica tutto) e lo importa: anagrafiche mancanti, form, bozza del
   processo, permessi (se il processo non ne ha già);
2. applica i permessi sulle anagrafiche (permessi-anagrafiche.json: non viaggiano nel pacchetto);
3. pubblica il processo (salvo --non-pubblicare: resta una bozza da pubblicare dallo Studio).

Chi lo lancia deve avere i permessi dello Studio (processi, form, pubblica, anagrafiche). Tutto finisce nel registro.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))


def installa(cli, admin, base, bpmn, chiave, pubblica=True):
    import pacchetto
    zip_path = base / (chiave + ".orditogram.zip")
    if pacchetto.main([str(base), "-o", str(zip_path)]) != 0: return 1
    r = cli.chiama("POST", "/api/admin/pacchetti?sovrascrivi=true", admin, {}, [("file", (zip_path.name, zip_path.read_bytes()))])
    print("Importato: form %s; anagrafiche: %s; permessi: %s" % (", ".join(r["formSalvati"]), r["anagrafiche"] or "-", r["permessi"] or "-"))
    pa = base / "permessi-anagrafiche.json"
    if pa.exists():
        for codice, regole in json.loads(pa.read_text(encoding="utf-8")).items():
            cli.chiama("PUT", "/api/admin/anagrafiche/%s/permessi" % codice, admin, regole)
            print("Permessi dell'anagrafica %s: %d regole" % (codice, len(regole)))
    if pubblica:
        r = cli.chiama("POST", "/api/admin/processes/deploy", admin, {}, [("bpmn", (bpmn.name, bpmn.read_bytes()))])
        print("Pubblicato %s versione %s" % (r["key"], r["version"]))
        for w in r.get("warnings", []): print("  avviso: %s" % (w.get("messaggio") if isinstance(w, dict) else w))
    else:
        print("Processo importato come bozza: pubblicalo dallo Studio")
    return 0


def main(argv):
    import argparse
    import collaudo
    import verifica
    ap = argparse.ArgumentParser()
    ap.add_argument("cartella")
    ap.add_argument("--url", required=True)
    ap.add_argument("--admin", required=True, help="utente:password")
    ap.add_argument("--non-pubblicare", action="store_true")
    a = ap.parse_args(argv)
    base = Path(a.cartella)
    utente, pw = a.admin.split(":", 1)
    cli = collaudo.Client(a.url, pw)
    bpmn = next(iter(list(base.glob("*.bpmn20.xml")) + list(base.glob("*.bpmn"))))
    forms = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted((base / "form").glob("*.json"))}
    chiave = verifica.analizza_bpmn(bpmn, forms, verifica.Problemi(), set(), set())["chiave"]
    try:
        return installa(cli, utente, base, bpmn, chiave, not a.non_pubblicare)
    except collaudo.Fallito as e:
        print("Installazione interrotta: %s" % e)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
