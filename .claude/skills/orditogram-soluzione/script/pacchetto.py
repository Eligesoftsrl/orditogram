#!/usr/bin/env python3
"""
Crea il pacchetto .orditogram.zip da una cartella di soluzione, nello stesso formato dell'esportazione dello Studio
(bozza/BozzaController): orditogram-pacchetto.json, processo.bpmn20.xml, form/<formKey>.json,
anagrafiche/<codice>.json, permessi.json, glossario.json, utenti-prova.json.

    python3 pacchetto.py <cartella-soluzione> [-o uscita.orditogram.zip]

Prima lancia verifica.py: il pacchetto si crea solo se non ci sono errori.
Nel pacchetto finiscono solo i form usati dal processo. Si importa da Studio → Processi → Importa pacchetto:
le anagrafiche si creano se mancano, i form si salvano, il processo diventa una bozza da pubblicare,
i permessi si applicano se il processo non ne ha già, le parole del glossario dove l'installazione non ne ha
scelte, gli attori della demo solo dove gli utenti di prova sono accesi.
"""
import json
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

import verifica


def main(argv):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("cartella")
    ap.add_argument("-o", "--uscita")
    ap.add_argument("--credenziali", default="protocollo-demo")
    ap.add_argument("--anagrafiche-esistenti", default="")
    ap.add_argument("--processi-pubblicati", default="")
    a = ap.parse_args(argv)
    base = Path(a.cartella)

    esito = verifica.main([a.cartella, "--credenziali", a.credenziali, "--anagrafiche-esistenti", a.anagrafiche_esistenti,
                           "--processi-pubblicati", a.processi_pubblicati])
    if esito != 0:
        print("\nPacchetto non creato: correggi gli errori.")
        return 1

    bpmn = next(iter(list(base.glob("*.bpmn20.xml")) + list(base.glob("*.bpmn"))))
    pb = verifica.Problemi()
    forms = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted((base / "form").glob("*.json"))}
    info = verifica.analizza_bpmn(bpmn, forms, pb, set(), set())
    chiave = info["chiave"]
    uscita = Path(a.uscita) if a.uscita else base / (chiave + ".orditogram.zip")

    anagrafiche = sorted((base / "anagrafiche").glob("*.json")) if (base / "anagrafiche").is_dir() else []
    manifest = {
        "formato": 1,
        "chiave": chiave,
        "nome": info["nome"] or chiave,
        "origine": "skill orditogram-soluzione",
        "esportatoDa": "orditogram-soluzione",
        "esportatoIl": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "form": info["form"],
    }
    if anagrafiche:
        manifest["anagrafiche"] = [json.loads(p.read_text(encoding="utf-8"))["codice"] for p in anagrafiche]

    with zipfile.ZipFile(uscita, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("processo.bpmn20.xml", bpmn.read_text(encoding="utf-8"))
        for fk in info["form"]:
            z.writestr("form/%s.json" % fk, json.dumps(forms[fk], ensure_ascii=False, indent=2))
        for p in anagrafiche:
            t = json.loads(p.read_text(encoding="utf-8"))
            # solo i campi che l'importazione legge (TipiAnagraficaService.Tipo)
            tipo = {k: t.get(k) for k in ("codice", "nome", "descrizione", "definizione")}
            tipo["attivo"] = t.get("attivo", True)
            z.writestr("anagrafiche/%s.json" % t["codice"], json.dumps(tipo, ensure_ascii=False, indent=2))
        # permessi, parole dell'installazione e attori della demo: li applica l'importazione (vedi BozzaController)
        for extra in ("permessi.json", "glossario.json", "utenti-prova.json"):
            if (base / extra).exists():
                z.writestr(extra, (base / extra).read_text(encoding="utf-8"))
        z.writestr("orditogram-pacchetto.json", json.dumps(manifest, ensure_ascii=False, indent=2))
    print("\nPacchetto creato: %s" % uscita)
    return 0


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).parent))
    sys.exit(main(sys.argv[1:]))
