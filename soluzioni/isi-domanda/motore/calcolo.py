"""Motore di calcolo ISI: punteggio, importi, assi ammessi, errori bloccanti, documenti richiesti.

Modulo puro (RNF-ARC-02): nessuna dipendenza da rete, database o interfaccia. Riceve i dati della domanda come
dizionario di testi (come li manda Orditogram) e le regole dell'annualità (configurazione/<anno>.json). Ogni
risultato porta il dettaglio con il codice della regola che lo ha prodotto.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP, ROUND_DOWN
from pathlib import Path

CARTELLA_CONFIG = Path(__file__).parent / "configurazione"
CENT = Decimal("0.01")
ASSI = ["1.1", "1.2", "2", "3", "4", "5.1", "5.2"]


def carica_regole(annualita: str = "2025") -> dict:
    return json.loads((CARTELLA_CONFIG / f"{annualita}.json").read_text(encoding="utf-8"))


# ------------------------------------------------------------------ lettura dei dati (tutti testi)

def dec(v, predefinito=None) -> Decimal | None:
    """Numero dal testo dell'interfaccia: accetta "1.500,50", "1500.5", "10,44"."""
    if v is None:
        return predefinito
    s = str(v).strip().replace("€", "").replace(" ", "")
    if not s:
        return predefinito
    if "," in s:
        s = s.replace(".", "").replace(",", ".")
    try:
        return Decimal(s)
    except Exception:
        return predefinito


def si(v) -> bool:
    return str(v).strip().lower() in ("true", "sì", "si", "1", "yes", "on")


def testo(v) -> str:
    return "" if v is None else str(v).strip()


MESI = {m: i for i, m in enumerate(("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"), 1)}


def data(v) -> date | None:
    """Data in formato AAAA-MM-GG, oppure come la scrive Orditogram nei testi dei connettori ("Mon Mar 01 00:00:00 UTC 2010")."""
    s = testo(v)
    try:
        return date.fromisoformat(s[:10])
    except ValueError:
        pass
    p = s.split()
    if len(p) == 6 and p[1] in MESI and p[2].isdigit() and p[5].isdigit():
        return date(int(p[5]), MESI[p[1]], int(p[2]))
    return None


def codice_asse(v) -> str:
    """"2 Rischi infortunistici" → "2"; "1.1" → "1.1"."""
    s = testo(v).split(" ")[0]
    return s if s in ASSI else ""


def codice_voce(v) -> str:
    """"b) Sostituzione di macchine" → "b"; "2a – Gasolio..." → "2a"."""
    s = testo(v)
    if not s:
        return ""
    return s.split(")")[0].split(" ")[0].strip().lower()


def centesimi(x: Decimal) -> Decimal:
    return x.quantize(CENT, rounding=ROUND_HALF_UP)


def euro(x: Decimal | None) -> str:
    """Formato italiano: 70.200,00 €."""
    if x is None:
        return "—"
    s = f"{centesimi(x):,.2f}"
    return s.replace(",", "X").replace(".", ",").replace("X", ".") + " €"


def punti(x: Decimal) -> str:
    s = format(x.normalize(), "f") if x != x.to_integral() else str(int(x))
    return s.replace(".", ",")


# ------------------------------------------------------------------ risultato

@dataclass
class Risultato:
    assiAmmessi: list = field(default_factory=list)
    assiNonAmmessi: dict = field(default_factory=dict)
    ula: Decimal | None = None
    microPiccola: bool = False
    punteggio: Decimal = Decimal(0)
    punteggioBeni: list = field(default_factory=list)
    dettaglioPunteggio: list = field(default_factory=list)
    importi: dict = field(default_factory=dict)
    dettaglioImporti: list = field(default_factory=list)
    avvisi: list = field(default_factory=list)
    errori: list = field(default_factory=list)
    documenti: list = field(default_factory=list)

    @property
    def registrabile(self) -> bool:
        return not self.errori

    def in_testo(self, regole: dict) -> dict:
        """Valori per Orditogram: testi brevi, numeri con il punto per le condizioni, testi lunghi a righe."""
        imp = self.importi
        return {
            "versioneRegole": regole["versioneRegole"],
            "punteggio": format(self.punteggio.normalize(), "f"),  # con il punto: per le condizioni del BPMN
            "punteggioTesto": punti(self.punteggio),
            "soglia": str(regole["parametri"]["soglia"]),
            "registrabile": "Sì" if self.registrabile else "No",
            "microPiccola": "Sì" if self.microPiccola else "No",
            "ula": "" if self.ula is None else str(self.ula),
            "contributoTotale": str(imp.get("contributoTotale", Decimal(0))),
            "contributoTotaleTesto": euro(imp.get("contributoTotale")),
            "contributoPrincipale": str(imp.get("contributoPrincipale", Decimal(0))),
            "contributoAggiuntivo": str(imp.get("contributoAggiuntivo", Decimal(0))),
            "spesaAmmissibile": str(imp.get("spesaAmmissibile", Decimal(0))),
            "anticipo": str(imp.get("anticipo", Decimal(0))),
            "fideiussione": str(imp.get("fideiussione", Decimal(0))),
            "assiAmmessi": ", ".join(self.assiAmmessi) or "nessuno",
            "assiNonAmmessi": "\n".join(f"Asse {a}: {m}" for a, m in self.assiNonAmmessi.items()),
            "riepilogo": (f"Punteggio {punti(self.punteggio)} su soglia {regole['parametri']['soglia']} · "
                          f"contributo {euro(imp.get('contributoTotale'))} · "
                          + ("registrabile" if self.registrabile else f"{len(self.errori)} errori bloccanti")),
            "dettaglioPunteggio": "\n".join(self.dettaglioPunteggio),
            "dettaglioImporti": "\n".join(self.dettaglioImporti),
            "avvisiCalcolo": "\n".join(self.avvisi) or "Nessun avviso",
            "erroriBloccanti": "\n".join(self.errori) or "Nessun errore bloccante",
            "documentiRichiesti": "\n".join(f"• {d}" for d in self.documenti),
        }


# ------------------------------------------------------------------ dimensione e assi

def ula_arrotondate(d: dict) -> Decimal | None:
    u = dec(d.get("ula"))
    return None if u is None else u.quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)  # R-ALL-01


def micro_piccola(ula: Decimal | None, d: dict, p: dict) -> bool:
    """R-DEST-06: meno di 50 ULA e fatturato o totale di bilancio ≤ 10 milioni."""
    if ula is None:
        return False
    lim = Decimal(p["microPiccolaFatturatoMassimo"])
    valori = [v for v in (dec(d.get("fatturato")), dec(d.get("totaleBilancio"))) if v is not None]
    return ula < p["microPiccolaUlaMassime"] and (not valori or any(v <= lim for v in valori))


def sezione_ateco(ateco: str, regole: dict) -> str:
    div = ateco[:2]
    for da, a, sez in regole["sezioniAteco"]:
        if da <= div <= a:
            return sez
    return ""


def prefisso_piu_lungo(ateco: str, tabella: dict):
    """R-DEST-05 e 9.4.7: i codici valgono come prefisso, conta il più lungo."""
    trovati = [k for k in tabella if ateco.startswith(k)]
    return max(trovati, key=len) if trovati else None


def assi_ammessi(d: dict, regole: dict, ula, mp: bool) -> tuple[list, dict]:
    ateco = testo(d.get("ateco"))
    sogg = testo(d.get("tipoSoggetto"))
    rapporto = testo(d.get("rapportoAssicurativo"))
    agricola = si(d.get("agricolaPrimaria"))
    settore4 = prefisso_piu_lungo(ateco, regole["asse4Ateco"]) is not None
    no = {}
    for a in ASSI:
        if rapporto in ("", "Nessuno"):
            no[a] = "nessun rapporto assicurativo con INAIL o INPS gestione agricola (R-DEST-12)"
        elif sogg == "ETS iscritto al RUNTS" and a != "1.1":
            no[a] = "gli ETS iscritti al RUNTS accedono solo all'Asse 1.1 tipologia d (R-DEST-02)"
        elif agricola and mp and a not in ("3", "5.1", "5.2"):
            no[a] = "le micro e piccole imprese agricole primarie accedono solo agli Assi 3 e 5 (R-DEST-03)"
        elif settore4 and mp and a in ("2", "5.1", "5.2"):
            no[a] = "le micro e piccole imprese dei settori dell'Asse 4 sono escluse dagli Assi 2 e 5 (R-DEST-04)"
        elif a == "4" and not (mp and settore4):
            no[a] = "riservato alle micro e piccole imprese con ATECO nella tabella dell'Asse 4 (R-DEST-05)"
        elif a in ("5.1", "5.2") and not (agricola and mp):
            no[a] = "riservato alle micro e piccole imprese agricole primarie (R-DEST-07)"
        elif a in ("5.1", "5.2") and rapporto == "INAIL":
            no[a] = "il rapporto assicurativo INAIL non è ammesso per l'Asse 5 (R-DEST-10)"
        elif rapporto == "INPS gestione agricola" and not (ateco.startswith("01.61")):
            if agricola and mp and ateco.startswith("01") and a not in ("3", "5.1", "5.2"):
                no[a] = "con rapporto INPS agricolo le micro e piccole agricole primarie accedono solo agli Assi 3 e 5 (R-DEST-11)"
            elif not (agricola and mp and ateco.startswith("01")) and a not in ("1.1", "1.2", "2", "3"):
                no[a] = "con rapporto INPS agricolo sono ammessi solo gli Assi 1.1, 1.2, 2 e 3 (R-DEST-11)"
        if a in no:
            continue
        if a == "1.2":
            if (dec(d.get("dipendentiImpresa"), Decimal(0)) <= 0) or (dec(d.get("dipendentiUnita"), Decimal(0)) <= 0):
                no[a] = "riservato alle imprese con dipendenti, anche nell'unità produttiva (R-REQ-04)"
            elif si(d.get("sgslUltimi3Anni")):
                no[a] = "l'impresa ha adottato un SGSL o MOG nei 3 anni precedenti (R-ALL-11)"
            elif si(d.get("concessione12Precedenti")):
                no[a] = "l'impresa ha avuto una concessione Asse 1.2 nelle 3 edizioni precedenti (R-REQ-14)"
    return [a for a in ASSI if a not in no], no


# ------------------------------------------------------------------ punteggio

def punti_dimensione(ula, d, regole, r: Risultato) -> Decimal:
    if ula is None:
        return Decimal(0)
    for f in regole["dimensioni"]:
        if ula > f["ulaDa"] and (f["ulaA"] is None or ula <= f["ulaA"]):
            p = Decimal(str(f["punti"]))
            valori = [v for v in (dec(d.get("fatturato")), dec(d.get("totaleBilancio"))) if v is not None]
            if f["limite"] is not None and valori and all(v > f["limite"] for v in valori):
                ridotti = Decimal(str(f["puntiRidotti"]))  # R-ALL-02, un decimale (H2)
                r.dettaglioPunteggio.append(
                    f"Dimensioni: {punti(ridotti)} ({ula} ULA, fatturato o bilancio oltre {euro(Decimal(f['limite']))}: "
                    f"{punti(p)} × {regole['parametri']['riduzioneDimensione']}) [R-ALL-02]")
                return ridotti
            r.dettaglioPunteggio.append(f"Dimensioni: {punti(p)} ({ula} ULA) [9.4.2]")
            return p
    return Decimal(0)


def punti_rischiosita(asse, d, regole, r: Risultato) -> Decimal:
    p = regole["parametri"]
    if asse == "4":
        pref = prefisso_piu_lungo(testo(d.get("ateco")), regole["asse4Ateco"])
        v = Decimal(regole["asse4Ateco"][pref]) if pref else Decimal(0)
        r.dettaglioPunteggio.append(f"Rischiosità: {punti(v)} (ATECO {d.get('ateco')}, prefisso {pref}) [9.4.7]")
        return v
    iscr = data(d.get("dataIscrizioneCciaa"))
    if iscr and iscr >= date.fromisoformat(p["dataIscrizioneRischiositaFissa"]):
        r.dettaglioPunteggio.append(f"Rischiosità: {p['rischiositaFissa']} (iscritta alla CCIAA dal 31/12/2023) [9.4.3]")
        return Decimal(p["rischiositaFissa"])
    tasso = dec(d.get("tassoMedio"))
    if tasso is None:
        r.dettaglioPunteggio.append(f"Rischiosità: {regole['rischiositaNonDefinita']} (tasso non definito) [9.4.3]")
        return Decimal(regole["rischiositaNonDefinita"])
    for f in regole["fasceTasso"]:
        if Decimal(str(f["da"])) <= tasso <= Decimal(str(f["a"])):
            r.dettaglioPunteggio.append(f"Rischiosità: {f['punti']} (tasso {punti(tasso)} ‰) [9.4.3]")
            return Decimal(f["punti"])
    r.dettaglioPunteggio.append(f"Rischiosità: {regole['rischiositaNonDefinita']} (tasso {punti(tasso)} ‰ fuori tabella) [9.4.3]")
    return Decimal(regole["rischiositaNonDefinita"])


def punti_tipologia(asse, d, regole, r: Risultato) -> Decimal:
    tab = regole["tipologie"].get(asse, {})
    if asse == "4":
        scelte = [k for k in ("a", "b", "c") if si(d.get(f"tipologia4{k}"))]
        if not scelte:
            r.errori.append("Scegli almeno una tipologia di intervento dell'Asse 4 (passo 3)")
            return Decimal(0)
        k = max(scelte, key=lambda c: tab[c][1])  # R-ALL-05
        r.dettaglioPunteggio.append(f"Tipologia: {tab[k][1]} (tipologia {k}, la più alta tra {', '.join(scelte)}) [R-ALL-05]")
        return Decimal(tab[k][1])
    k = codice_voce(d.get("tipologia" + asse.replace(".", "")))
    if k not in tab:
        r.errori.append(f"Scegli la tipologia di intervento dell'Asse {asse} (passo 3)")
        return Decimal(0)
    if testo(d.get("tipoSoggetto")) == "ETS iscritto al RUNTS" and not (asse == "1.1" and k == "d"):
        r.errori.append("Gli ETS iscritti al RUNTS possono scegliere solo l'Asse 1.1 tipologia d (R-DEST-02)")
    r.dettaglioPunteggio.append(f"Tipologia: {tab[k][1]} (tipologia {k}) [9.4.6]")
    return Decimal(tab[k][1])


def punti_comuni(asse, d, regole, r: Risultato, scrivi=True) -> Decimal:
    tot = Decimal(0)
    ps = testo(d.get("partiSociali")) or "Nessuna"
    v = Decimal(regole["partiSociali"].get(ps, 0))
    tot += v
    if scrivi and v:
        r.dettaglioPunteggio.append(f"Parti sociali: {v} ({ps}) [9.4.4]")
    if si(d.get("rls")):
        v = Decimal(regole["rls"][asse])
        tot += v
        if scrivi:
            r.dettaglioPunteggio.append(f"RLS/RLST: {v} [9.4.4]")
    iso_aggiuntivo = asse in ("1.1", "4") and "45001" in testo(d.get("aggiuntivo" + asse.replace(".", "")))
    for campo, c in regole["certificazioni"].items():
        if not si(d.get(campo)) or asse not in c["assi"]:
            continue
        if campo == "certIso45001" and iso_aggiuntivo:
            if scrivi:
                r.avvisi.append("Certificazione ISO 45001: 0 punti, perché hai scelto l'intervento aggiuntivo "
                                "«Adozione SGSL ISO 45001» (R-ALL-06)")
            continue
        tot += c["punti"]
        if scrivi:
            r.dettaglioPunteggio.append(f"Certificazione {c['nome']}: {c['punti']} [9.4.5]")
    return tot


def punti_bonus(asse, d, regole, r: Risultato) -> Decimal:
    if asse not in regole["bonusAtecoAssi"]:
        return Decimal(0)
    prefissi = regole["bonusAtecoRegionale"].get(testo(d.get("regione")), [])
    if any(testo(d.get("ateco")).startswith(p) for p in prefissi):
        r.dettaglioPunteggio.append(f"Bonus ATECO regionale: {regole['bonusAtecoPunti']} [9.4.8]")
        return Decimal(regole["bonusAtecoPunti"])
    return Decimal(0)


def beni_asse5(d: dict) -> list[dict]:
    beni = []
    for i in (1, 2):
        if i == 2 and not si(d.get("secondoBene")):
            break
        beni.append({
            "n": i,
            "tipo": testo(d.get(f"bene{i}Tipo")),
            "misura": codice_voce(d.get(f"bene{i}Misura")),
            "fattore": codice_voce(d.get(f"bene{i}Fattore")),
            "preventivo": dec(d.get(f"bene{i}Preventivo"), Decimal(0)),
            "listino": dec(d.get(f"bene{i}Listino")),
            "modalita": testo(d.get(f"bene{i}Modalita")),
            "ricavoPermuta": dec(d.get(f"bene{i}RicavoPermuta"), Decimal(0)),
            "annoSostituito": dec(d.get(f"bene{i}AnnoSostituito")),
            "potenzaKw": dec(d.get(f"bene{i}PotenzaKw")),
        })
    return beni


def punteggio_asse5(asse, d, regole, r: Risultato) -> Decimal:
    soglia = regole["parametri"]["soglia"]
    comuni = punti_comuni(asse, d, regole, r, scrivi=False)
    beni = beni_asse5(d)
    if len(beni) == 2 and [beni[0]["tipo"], beni[1]["tipo"]] not in regole["combinazioniBeniAsse5"] \
            and [beni[1]["tipo"], beni[0]["tipo"]] not in regole["combinazioniBeniAsse5"]:
        r.errori.append(f"Combinazione di beni non ammessa: {beni[0]['tipo']} + {beni[1]['tipo']} (R-ALL-07)")
    migliore = Decimal(0)
    for b in beni:
        m = regole["misureAsse5"].get(b["misura"])
        f = regole["fattoriAsse5"].get(b["fattore"])
        if not m or not f:
            r.errori.append(f"Bene {b['n']}: scegli misura e fattore di rischio (R-ALL-07)")
            b["punteggio"], b["ammesso"] = Decimal(0), False
            continue
        if b["tipo"] == "Mietitrebbiatrice" and b["fattore"] in regole["fattoriNonMietitrebbiatrice"]:
            r.errori.append(f"Bene {b['n']}: per una mietitrebbiatrice i fattori 2c e 2d non sono ammessi (R-ALL-08)")
        tot = Decimal(m[1]) + Decimal(f[1]) + comuni
        b["punteggio"], b["ammesso"] = tot, tot >= soglia
        r.punteggioBeni.append(tot)
        r.dettaglioPunteggio.append(
            f"Bene {b['n']} ({b['tipo'] or 'tipo non indicato'}): misura {b['misura']} {m[1]} + fattore {b['fattore']} {f[1]}"
            f" + criteri comuni {punti(comuni)} = {punti(tot)} → {'ammesso' if b['ammesso'] else 'sotto soglia'} [9.4.1]")
        migliore = max(migliore, tot)
    d["_beni"] = beni
    return migliore


# ------------------------------------------------------------------ importi

def macchine(d: dict, regole: dict, r: Risultato) -> tuple[Decimal, int]:
    p = regole["parametri"]
    tot, n = Decimal(0), 0
    for i in (1, 2):
        if i == 2 and not si(d.get("secondaMacchina")):
            break
        prev = dec(d.get(f"macchina{i}Preventivo"))
        lis = dec(d.get(f"macchina{i}Listino"))
        if prev is None or lis is None:
            continue
        n += 1
        base = lis
        if si(d.get(f"macchina{i}Trattore")):
            acc = dec(d.get(f"macchina{i}ListinoAccessori"), Decimal(0))
            acc_max = centesimi(lis * Decimal(str(p["accessoriTrattorePercentuale"])))
            if acc > acc_max:
                r.avvisi.append(f"Macchina {i}: listino accessori ridotto a {euro(acc_max)}, 30% del trattore base (R-CALC-03)")
                acc = acc_max
            base = lis + acc
        tetto = centesimi(base * Decimal(str(p["macchinaPercentualeListino"])))
        amm = min(prev, tetto)  # R-CALC-02
        if amm < prev:
            r.avvisi.append(f"Macchina {i}: spesa ridotta a {euro(amm)}, l'80% del listino (R-CALC-02)")
        r.dettaglioImporti.append(f"Macchina {i}: min(preventivo {euro(prev)}; 80% listino {euro(tetto)}) = {euro(amm)} [R-CALC-02]")
        tot += amm
    return tot, n


def spese_progetto(asse, d, regole, r: Risultato) -> tuple[Decimal, bool]:
    p = regole["parametri"]
    altre = dec(d.get("speseProgetto"), Decimal(0))
    tot_macchine, n = macchine(d, regole, r) if asse in ("1.1", "2", "4") else (Decimal(0), 0)
    solo_macchine = n > 0 and altre == 0
    tot = altre + tot_macchine
    if asse == "3":
        cop = dec(d.get("speseCopertura"), Decimal(0))
        mq = dec(d.get("mqCopertura"), Decimal(0))
        cop_max = mq * p["asse3CoperturaEuroMq"]
        if cop > cop_max:
            r.avvisi.append(f"Spese per la copertura ridotte a {euro(cop_max)}: massimo {p['asse3CoperturaEuroMq']} €/mq (R-ALL-09)")
            cop = cop_max
        con = dec(d.get("speseControsoffitto"), Decimal(0))
        con_max = dec(d.get("mqControsoffitto"), Decimal(0)) * p["asse3ControsoffittoEuroMq"]
        if con > con_max:
            r.avvisi.append(f"Spese per il controsoffitto ridotte a {euro(con_max)}: massimo {p['asse3ControsoffittoEuroMq']} €/mq (R-ALL-09)")
            con = con_max
        acc = dec(d.get("speseAccessorie"), Decimal(0))
        acc_max = centesimi(cop * Decimal(str(p["asse3AccessoriePercentuale"])))
        if acc > acc_max:
            r.avvisi.append(f"Spese per parapetti, ancoraggi e scale ridotte a {euro(acc_max)}: 10% della copertura (R-ALL-09)")
            acc = acc_max
        if cop or con or acc:
            r.dettaglioImporti.append(f"Asse 3: copertura {euro(cop)} + controsoffitto {euro(con)} + accessorie {euro(acc)} [R-ALL-09]")
        tot += cop + con + acc
    return tot, solo_macchine


def spese_tecniche(asse, base: Decimal, solo_macchine: bool, d, regole, r: Risultato) -> Decimal:
    p = regole["parametri"]
    tec = dec(d.get("speseTecniche"), Decimal(0))
    per = dec(d.get("costoPerizia"), Decimal(0))
    if per > p["periziaMassimo"]:
        r.avvisi.append(f"Costo della perizia ridotto a {euro(Decimal(p['periziaMassimo']))}: massimo previsto (R-ALL-03)")
        per = Decimal(p["periziaMassimo"])
    if asse == "1.2":
        if tec or per:
            r.avvisi.append("Asse 1.2: le spese tecniche non sono ammesse (R-ALL-03)")
        return Decimal(0)
    if asse in ("5.1", "5.2"):
        if tec:
            r.avvisi.append("Asse 5: l'unica spesa tecnica ammessa è la perizia (R-ALL-03)")
        return per
    if solo_macchine and asse in ("1.1", "2", "4"):
        perc, mass = Decimal(str(p["speseTecnicheSoloMacchinePercentuale"])), Decimal(p["speseTecnicheSoloMacchineMassimo"])
    else:
        perc, mass = Decimal(str(p["speseTecnichePercentuale"])), Decimal(p["speseTecnicheMassimo"])
    richieste = tec + per
    tetto = min(centesimi(base * perc), mass)
    if richieste > tetto:
        r.avvisi.append(f"Spese tecniche ridotte a {euro(tetto)}: massimo {int(perc * 100)}% delle spese di progetto "
                        f"e non oltre {euro(mass)} (R-ALL-03)")
        return tetto
    return richieste


def valore_parametrico(d: dict, regole: dict) -> tuple[Decimal | None, str]:
    ateco = testo(d.get("ateco"))
    sez = sezione_ateco(ateco, regole)
    cod = sez + ateco  # es. "C16.23"
    compl = ""
    lungh = -1
    for livello, codici in regole["macrosettori12"].items():
        for c in codici:
            c_norm = c if len(c) == 1 else c[0] + c[1:]
            if cod.startswith(c_norm) and len(c_norm) > lungh:
                compl, lungh = livello, len(c_norm)
    if not compl:
        compl = "BASSA"
    dip = dec(d.get("dipendentiImpresa"), Decimal(0))
    for f in regole["valoreParametrico12"]:
        if dip >= f["da"] and (f["a"] is None or dip <= f["a"]):
            return Decimal(str(f[compl])), compl
    return None, compl


def importi(asse, d, regole, r: Risultato, mp: bool):
    p = regole["parametri"]
    info = regole["assi"][asse]
    perc = Decimal(str(info["percentuale"]))
    massimo = Decimal(p["contributoMassimo"])

    if asse in ("5.1", "5.2"):
        if asse == "5.2" and not (si(d.get("giovaneEta")) and si(d.get("giovaneFormazione")) and si(d.get("giovaneCapoAzienda"))):
            perc = Decimal(str(info["percentualeSenzaRequisiti"]))
            r.avvisi.append("Asse 5.2: manca almeno un requisito del giovane agricoltore, il contributo è al 65% (R-CALC-06)")
        quota = Decimal(str(p["asse5QuotaImpresaGiovani"] if perc == Decimal("0.80") else p["asse5QuotaImpresa"]))
        spesa_tot, contributo = Decimal(0), Decimal(0)
        for b in d.get("_beni", []):
            if not b.get("ammesso"):
                r.dettaglioImporti.append(f"Bene {b['n']}: escluso dal contributo, punteggio sotto soglia [RF-13 CA-02]")
                continue
            spesa = b["preventivo"]
            if b["listino"] is not None:
                spesa = min(spesa, centesimi(b["listino"] * Decimal(str(p["macchinaPercentualeListino"]))))
            c = centesimi(spesa * perc)
            riga = f"Bene {b['n']}: spesa {euro(spesa)} × {int(perc * 100)}% = {euro(c)}"
            if b["modalita"] == "Permuta":
                carico = centesimi(spesa * quota)
                if b["ricavoPermuta"] > carico:
                    rid = b["ricavoPermuta"] - carico
                    c -= rid
                    riga += f", ridotto di {euro(rid)} perché la permuta ({euro(b['ricavoPermuta'])}) supera la quota a carico ({euro(carico)}) [R-CALC-07]"
            r.dettaglioImporti.append(riga + f" = {euro(c)}")
            spesa_tot += spesa
            contributo += c
        tec = spese_tecniche(asse, spesa_tot, False, d, regole, r)
        if tec:
            c_tec = centesimi(tec * perc)
            r.dettaglioImporti.append(f"Perizia: {euro(tec)} × {int(perc * 100)}% = {euro(c_tec)}")
            contributo += c_tec
        spesa_amm = spesa_tot + tec
    else:
        base, solo_macchine = spese_progetto(asse, d, regole, r)
        if asse == "1.2":
            vp, compl = valore_parametrico(d, regole)
            if vp is not None:
                tetto = vp * p["asse12ConsulenzaEuro"] + vp * p["asse12CertificazioneEuro"]
                r.dettaglioImporti.append(f"Asse 1.2: valore parametrico {punti(vp)} (complessità {compl}) → spesa massima {euro(tetto)} [R-ALL-10]")
                if base > tetto:
                    r.avvisi.append(f"Spese di progetto ridotte a {euro(tetto)}: massimo dell'Asse 1.2 (R-ALL-10)")
                    base = tetto
        tec = spese_tecniche(asse, base, solo_macchine, d, regole, r)
        spesa_amm = centesimi(base + tec)  # R-CALC-01
        r.dettaglioImporti.append(f"Spesa ammissibile: progetto {euro(base)} + spese tecniche {euro(tec)} = {euro(spesa_amm)} [R-CALC-01]")
        contributo = centesimi(spesa_amm * perc)
        r.dettaglioImporti.append(f"Contributo principale: {euro(spesa_amm)} × {int(perc * 100)}% = {euro(contributo)} [R-CALC-04]")

    if contributo > massimo:
        r.avvisi.append(f"Contributo ridotto a {euro(massimo)}: massimo previsto dall'Avviso (R-CALC-04)")
        contributo = massimo
    minimo_ok = contributo >= p["contributoMinimo"]
    if asse == "1.2" and dec(d.get("dipendentiImpresa"), Decimal(0)) < p["dipendentiSenzaMinimoAsse12"]:
        minimo_ok = True  # H1: meno di 50 dipendenti, nessun minimo
    if not minimo_ok:
        r.errori.append(f"Contributo {euro(contributo)} inferiore al minimo di {euro(Decimal(p['contributoMinimo']))} (R-CALC-04)")

    # intervento aggiuntivo (R-CALC-05)
    agg = Decimal(0)
    scelta = testo(d.get("aggiuntivo" + asse.replace(".", "")))
    if scelta and scelta != "Nessuno" and info["aggiuntivi"]:
        spesa_agg = dec(d.get("speseAggiuntivo"), Decimal(0))
        agg = centesimi(spesa_agg * Decimal(str(p["aggiuntivoPercentuale"])))
        tetto = min(Decimal(p["aggiuntivoMassimo"]), massimo - contributo, contributo)
        if agg > tetto:
            r.avvisi.append(f"Contributo aggiuntivo ridotto a {euro(tetto)} (R-CALC-05)")
            agg = tetto
        r.dettaglioImporti.append(f"Contributo aggiuntivo ({scelta}): {euro(spesa_agg)} × 80% → {euro(agg)} [R-CALC-05]")
    totale = contributo + agg

    # anticipo e fideiussione (R-CALC-08, R-CALC-09)
    anticipo = Decimal(0)
    if si(d.get("anticipo")):
        if si(d.get("noleggioPattoAcquisto")):
            r.avvisi.append("Con il noleggio con patto d'acquisto l'anticipo non è richiedibile (R-CALC-08)")
        elif mp and info["anticipo70"]:
            anticipo = centesimi(totale * Decimal(str(p["anticipoPercentualeMicroPiccole"])))
        elif totale >= p["anticipoSoglia"]:
            anticipo = centesimi(totale * Decimal(str(p["anticipoPercentuale"])))
        else:
            r.avvisi.append(f"Anticipo non spettante: contributo inferiore a {euro(Decimal(p['anticipoSoglia']))} (R-CALC-08)")
    fide = centesimi(anticipo * Decimal(str(p["fideiussioneMoltiplicatore"])))
    if anticipo:
        r.dettaglioImporti.append(f"Anticipo {euro(anticipo)}, fideiussione {euro(fide)} [R-CALC-08, R-CALC-09]")
    r.importi = {"spesaAmmissibile": spesa_amm, "contributoPrincipale": contributo, "contributoAggiuntivo": agg,
                 "contributoTotale": totale, "anticipo": anticipo, "fideiussione": fide}


# ------------------------------------------------------------------ controlli, dichiarazioni, documenti

def giorni_lavorativi_prima(fine: date, n: int, festivita: set) -> date:
    g = fine
    contati = 0
    while contati < n:
        g -= timedelta(days=1)
        if g.weekday() < 5 and g.isoformat() not in festivita:
            contati += 1
    return g


def controlli_registrazione(asse, d, regole, r: Risultato):
    p = regole["parametri"]
    # RF-03: associazione al portale almeno 5 giorni lavorativi prima della chiusura
    chiusura = datetime.fromisoformat(regole["calendario"]["chiusuraCompilazione"]).date()
    limite = giorni_lavorativi_prima(chiusura, p["giorniLavorativiAssociazionePortale"], set(regole["festivita"]))
    ass = data(d.get("dataAssociazionePortale"))
    if ass and ass > limite:
        r.errori.append("Registrazione al portale avvenuta oltre il termine previsto dall'art. 12 dell'Avviso")
    # forza lavoro minima (R-REQ-01)
    if r.ula is not None and r.ula < 1 and not si(d.get("dittaSenzaDipendenti")) and not si(d.get("attivitaStagionale")):
        r.errori.append("Forza lavoro inferiore a 1 ULA (R-REQ-01)")
    # dichiarazioni (RF-16)
    agricola_2135 = si(d.get("agricolaPrimaria"))
    sostituzione = any(testo(d.get(f"bene{i}Modalita")) in ("Rottamazione", "Permuta") for i in (1, 2)) or \
        bool(dec(d.get("macchina1AnnoSostituito")))
    for x in regole["dichiarazioni"]:
        if x["assi"] != "tutti" and asse not in x["assi"]:
            continue
        if x.get("soloConSostituzione") and not sostituzione:
            continue
        if x.get("nonAgricole2135") and agricola_2135:
            continue
        if not si(d.get(x["campo"])):
            r.errori.append(f"Manca la dichiarazione {x['codice']} (passo 7)")
    prec = testo(d.get("finanziamentiPrecedenti"))
    if prec and prec != "Nessuno" and asse != "1.2" and codice_asse(d.get("assePrecedente")) != "1.2" \
            and not si(d.get("rinunciaPec")):
        r.errori.append("Finanziamento ISI precedente: serve la rinuncia via PEC (R-REQ-13)")
    if not si(d.get("nessunaSpesaNonAmmessa")):
        r.errori.append("Conferma che il piano economico non contiene spese non ammesse (passo 6)")
    # 9.6: beni sostituiti
    for b in d.get("_beni", []):
        anno, kw = b.get("annoSostituito"), b.get("potenzaKw")
        if b["tipo"] == "Trattore" and anno is not None:
            if anno >= 2013:
                r.errori.append(f"Bene {b['n']}: trattori immessi sul mercato dal 2013 non sono sostituibili (9.6)")
            elif anno < 2005 and b["modalita"] == "Permuta":
                r.errori.append(f"Bene {b['n']}: trattori immessi prima del 2005 solo con rottamazione (9.6)")
        if b["tipo"] == "Trattore" and kw is not None and kw > 130:
            r.errori.append(f"Bene {b['n']}: potenza massima 130 kW (9.6)")
    if asse == "4" and testo(d.get("macchina1Modalita")) == "Permuta":
        r.errori.append("Asse 4: il bene sostituito va rottamato, la permuta non è ammessa (9.6)")


def documenti_richiesti(asse, d, regole) -> list[str]:
    ets = testo(d.get("tipoSoggetto")).startswith("ETS")
    cond = {
        "sempre": True, "perizia": asse != "1.2", "asse12": asse == "1.2", "nonEts": not ets, "ets": ets,
        "asse5": asse in ("5.1", "5.2"),
        "partiSociali": (testo(d.get("partiSociali")) or "Nessuna") != "Nessuna", "rls": si(d.get("rls")),
        "dpi": asse == "2" and "DPI" in testo(d.get("aggiuntivo2")),
        "macchine": bool(dec(d.get("macchina1Preventivo"))) or asse in ("5.1", "5.2"),
        "catastrofale": not si(d.get("agricolaPrimaria")),
    }
    out = [x["nome"] for x in regole["documenti"] if cond.get(x["quando"])]
    for campo, c in regole["certificazioni"].items():
        if si(d.get(campo)) and asse in c["assi"]:
            out.append(c["documento"])
    return out


# ------------------------------------------------------------------ ingresso

def calcola(d: dict, regole: dict | None = None) -> Risultato:
    regole = regole or carica_regole(testo(d.get("annualita")) or "2025")
    d = dict(d)
    r = Risultato()
    r.ula = ula_arrotondate(d)
    if r.ula is not None and r.ula < 1 and si(d.get("attivitaStagionale")):
        r.ula = Decimal("1.0")  # RF-11 CA-04
    r.microPiccola = micro_piccola(r.ula, d, regole["parametri"])
    r.assiAmmessi, r.assiNonAmmessi = assi_ammessi(d, regole, r.ula, r.microPiccola)

    asse = codice_asse(d.get("asse"))
    if not asse:
        r.errori.append("Scegli l'asse di finanziamento (passo 3)")
        return r
    if asse not in r.assiAmmessi:
        r.errori.append(f"Asse {asse} non ammesso: {r.assiNonAmmessi.get(asse)}")

    if asse in ("5.1", "5.2"):
        r.punteggio = punteggio_asse5(asse, d, regole, r)
    else:
        tot = Decimal(0)
        if asse != "4" or (r.ula is not None and r.ula <= 50):
            tot += punti_dimensione(r.ula, d, regole, r)
        tot += punti_rischiosita(asse, d, regole, r)
        tot += punti_tipologia(asse, d, regole, r)
        tot += punti_comuni(asse, d, regole, r)
        tot += punti_bonus(asse, d, regole, r)
        r.punteggio = tot
    r.dettaglioPunteggio.append(f"Totale: {punti(r.punteggio)}")
    soglia = regole["parametri"]["soglia"]
    if r.punteggio < soglia:
        r.errori.insert(0, f"Punteggio {punti(r.punteggio)} inferiore alla soglia di {soglia}")

    importi(asse, d, regole, r, r.microPiccola)
    controlli_registrazione(asse, d, regole, r)
    r.documenti = documenti_richiesti(asse, d, regole)
    return r


def tronca_euro(x: Decimal) -> int:
    """R-CALC-10: negli elenchi l'importo è troncato all'euro."""
    return int(Decimal(x).quantize(Decimal(1), rounding=ROUND_DOWN))
