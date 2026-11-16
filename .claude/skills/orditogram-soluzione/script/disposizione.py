#!/usr/bin/env python3
"""
Disegna il diagramma (BPMNDiagram) di un BPMN che non ce l'ha, o lo rifà da capo con --rifai.

    python3 disposizione.py processo.bpmn20.xml [--rifai]

Lo Studio di Orditogram (bpmn-js) mostra solo gli elementi che hanno una forma nel diagramma: un BPMN scritto a mano
senza BPMNDiagram si apre come una tela vuota. Questo script mette i passi in colonne da sinistra a destra seguendo
le frecce, i sottoprocessi espansi con il loro contenuto, gli eventi di bordo sul lato inferiore dell'attività,
le frecce che tornano indietro passano sotto. Il risultato è leggibile; l'analista può sempre ritoccarlo nello Studio.
"""
import sys
import xml.etree.ElementTree as ET

BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
NS = {
    "": BPMN,
    "xsi": "http://www.w3.org/2001/XMLSchema-instance",
    "activiti": "http://activiti.org/bpmn",
    "orditogram": "http://orditogram.it/bpmn",
    "bpmndi": "http://www.omg.org/spec/BPMN/20100524/DI",
    "dc": "http://www.omg.org/spec/DD/20100524/DC",
    "di": "http://www.omg.org/spec/DD/20100524/DI",
}
for k, v in NS.items():
    ET.register_namespace(k, v)
BDI, DC, DI = NS["bpmndi"], NS["dc"], NS["di"]

NODI = {"startEvent", "endEvent", "userTask", "serviceTask", "exclusiveGateway", "parallelGateway",
        "subProcess", "intermediateCatchEvent", "callActivity", "boundaryEvent", "task", "scriptTask",
        "inclusiveGateway", "eventBasedGateway", "intermediateThrowEvent", "manualTask", "sendTask", "receiveTask",
        "businessRuleTask"}
GAP_X, GAP_Y, PAD = 70, 70, 40


def loc(el):
    return el.tag.split("}")[-1]


def dimensione(tipo):
    if tipo.endswith("Event"): return 36, 36
    if tipo.endswith("Gateway"): return 50, 50
    return 100, 80


class Box:
    def __init__(self, el):
        self.el, self.id, self.tipo = el, el.get("id"), loc(el)
        self.w, self.h = dimensione(self.tipo)
        self.x = self.y = 0
        self.figli = []          # per i sottoprocessi: i Box interni


def disponi(contenitore, flussi_tutti):
    """Dispone i nodi di un contenitore; restituisce (boxes per id, larghezza, altezza) in coordinate relative."""
    nodi = [c for c in contenitore if c.tag.startswith("{%s}" % BPMN) and loc(c) in NODI]
    bordi = [n for n in nodi if loc(n) == "boundaryEvent"]
    principali = [n for n in nodi if loc(n) != "boundaryEvent"]
    box = {n.get("id"): Box(n) for n in nodi}
    ospite = {b.get("id"): b.get("attachedToRef") for b in bordi}
    ids = {n.get("id") for n in principali}

    # sottoprocessi: prima il contenuto, poi la dimensione
    interni = {}
    for n in principali:
        if loc(n) == "subProcess":
            b_int, f_int, w, h = disponi(n, flussi_tutti)
            interni[n.get("id")] = (b_int, f_int)
            box[n.get("id")].w, box[n.get("id")].h = max(w + 2 * PAD, 200), max(h + 2 * PAD, 120)

    flussi = [f for f in contenitore if loc(f) == "sequenceFlow"]
    succ = {i: [] for i in ids}
    for f in flussi:
        s, t = f.get("sourceRef"), f.get("targetRef")
        s = ospite.get(s, s)
        if s in succ and t in ids: succ[s].append((t, f.get("sourceRef") in ospite))

    # archi all'indietro con una visita in profondità dagli inizi
    stato, indietro, ordine = {}, set(), []
    inizi = [n.get("id") for n in principali if loc(n) == "startEvent"] + \
            [i for i in ids if not any(i == t for v in succ.values() for t, _ in v)]

    def dfs(u):
        stato[u] = 1
        for v, _ in succ[u]:
            if stato.get(v) == 1: indietro.add((u, v))
            elif v not in stato: dfs(v)
        stato[u] = 2
        ordine.append(u)

    sys.setrecursionlimit(10000)
    for i in inizi + sorted(ids):
        if i not in stato: dfs(i)
    topo = list(reversed(ordine))

    colonna = {i: 0 for i in ids}
    for u in topo:
        for v, _ in succ[u]:
            if (u, v) not in indietro: colonna[v] = max(colonna[v], colonna[u] + 1)

    # righe: la strada principale resta sulla riga 0, le altre scendono; le strade dei bordi partono più in basso
    riga, occupate = {}, set()
    for u in topo:
        if u not in riga:
            r = 0
            while (colonna[u], r) in occupate: r += 1
            riga[u] = r; occupate.add((colonna[u], r))
        for k, (v, da_bordo) in enumerate(succ[u]):
            if v in riga or (u, v) in indietro: continue
            r = riga[u] + (1 if da_bordo else 0)
            while (colonna[v], r) in occupate: r += 1
            riga[v] = r; occupate.add((colonna[v], r))

    ncol = max(colonna.values(), default=0) + 1
    nrig = max(riga.values(), default=0) + 1
    larg = [max([box[i].w for i in ids if colonna[i] == c] or [0]) for c in range(ncol)]
    alt = [max([box[i].h for i in ids if riga[i] == r] or [36]) for r in range(nrig)]
    xs = [sum(larg[:c]) + GAP_X * c for c in range(ncol)]
    ys = [sum(alt[:r]) + GAP_Y * r for r in range(nrig)]
    for i in ids:
        b = box[i]
        b.x = xs[colonna[i]] + (larg[colonna[i]] - b.w) // 2
        b.y = ys[riga[i]] + (alt[riga[i]] - b.h) // 2
    # eventi di bordo sul lato inferiore
    conta = {}
    for b in bordi:
        h = box.get(b.get("attachedToRef"))
        if h is None: continue
        k = conta.get(h.id, 0); conta[h.id] = k + 1
        bb = box[b.get("id")]
        bb.x = h.x + h.w - 36 - 10 - k * 46 if h.tipo != "subProcess" else h.x + 30 + k * 60
        bb.y = h.y + h.h - 18
    w = (xs[-1] + larg[-1]) if ids else 0
    hh = (ys[-1] + alt[-1]) if ids else 0

    # frecce: i tratti verticali corrono nei corridoi tra le colonne, quelli orizzontali nei corridoi tra le righe,
    # così non attraversano mai un passo. Le frecce che tornano indietro passano sotto tutto.
    frecce = {}
    def gap_dopo(c): return xs[c] + larg[c] + GAP_X // 2
    def gap_prima(c): return xs[c] - GAP_X // 2
    def gap_sotto(r): return ys[r] + alt[r] + GAP_Y // 2
    def gap_sopra(r): return ys[r] - GAP_Y // 2
    fondo = hh + 25
    for n, f in enumerate(flussi):
        so, ta = f.get("sourceRef"), f.get("targetRef")
        if so not in box or ta not in ids: continue
        s, t = box[so], box[ta]
        sh = ospite.get(so, so)
        if sh not in ids: continue
        cs, rs, ct, rt = colonna[sh], riga[sh], colonna[ta], riga[ta]
        d = 6 * (n % 5) - 12            # piccolo scarto per non sovrapporre frecce parallele
        ty = t.y + t.h // 2
        if so in ospite:                # dall'evento di bordo: giù nel corridoio sotto la riga, poi verso il passo
            bx, by = s.x + s.w // 2, s.y + s.h
            gy = gap_sotto(rs) + d
            if ct > cs or (ct == cs and rt > rs):
                gx = gap_prima(ct) + d if ct > cs else t.x + t.w // 2
                pts = [(bx, by), (bx, gy), (gx, gy)]
                pts += [(gx, ty), (t.x, ty)] if ct > cs else [(gx, t.y)]
            else:
                pts = [(bx, by), (bx, fondo + d), (gap_prima(ct) + d, fondo + d), (gap_prima(ct) + d, ty), (t.x, ty)]
        elif ct <= cs:                  # torna indietro
            sy, gx = s.y + s.h // 2, gap_dopo(cs) + d
            pts = [(s.x + s.w, sy), (gx, sy), (gx, fondo + d), (gap_prima(ct) + d, fondo + d),
                   (gap_prima(ct) + d, ty), (t.x, ty)]
        else:
            sx, sy = s.x + s.w, s.y + s.h // 2
            libera = rs == rt and all(not (colonna[i] == c and riga[i] == rs) for i in ids for c in range(cs + 1, ct))
            if libera and abs(sy - ty) < 2:
                pts = [(sx, sy), (t.x, sy)]
            elif ct == cs + 1:
                gx = gap_dopo(cs) + d
                pts = [(sx, sy), (gx, sy), (gx, ty), (t.x, ty)]
            else:
                gx1, gx2 = gap_dopo(cs) + d, gap_prima(ct) + d
                gy = (gap_sotto(rs) if rt >= rs else gap_sopra(rs)) + d
                pts = [(sx, sy), (gx1, sy), (gx1, gy), (gx2, gy), (gx2, ty), (t.x, ty)]
        frecce[f.get("id")] = pts
    # contenuto dei sottoprocessi spostato dentro il riquadro
    for sp, (b_int, f_int) in interni.items():
        ox, oy = box[sp].x + PAD, box[sp].y + PAD
        for b in b_int.values():
            b.x += ox; b.y += oy
            box[b.id] = b
        for k, pts in f_int.items():
            frecce[k] = [(x + ox, y + oy) for x, y in pts]
    return box, frecce, w, hh + 60


def main(argv):
    if not argv:
        print(__doc__); return 2
    path, rifai = argv[0], "--rifai" in argv
    # il BPMN è un file della soluzione, letto in locale; expat non risolve entità esterne
    albero = ET.parse(path, parser=ET.XMLParser(target=ET.TreeBuilder(insert_comments=True)))  # nosemgrep: python.lang.security.use-defused-xml-parse.use-defused-xml-parse
    radice = albero.getroot()
    vecchi = radice.findall("{%s}BPMNDiagram" % BDI)
    if vecchi and not rifai:
        print("Il diagramma c'è già: usa --rifai per ridisegnarlo"); return 0
    for v in vecchi: radice.remove(v)
    processo = next(p for p in radice.findall("{%s}process" % BPMN) if p.get("isExecutable", "true") != "false")
    box, frecce, w, h = disponi(processo, None)
    for b in box.values():
        b.x += 80; b.y += 80

    diag = ET.SubElement(radice, "{%s}BPMNDiagram" % BDI, id="Diagram_" + processo.get("id"))
    piano = ET.SubElement(diag, "{%s}BPMNPlane" % BDI, id="Plane_" + processo.get("id"), bpmnElement=processo.get("id"))
    # prima i sottoprocessi (sotto), poi il resto
    for b in sorted(box.values(), key=lambda b: b.tipo != "subProcess"):
        attr = {"id": "s_" + b.id, "bpmnElement": b.id}
        if b.tipo == "subProcess": attr["isExpanded"] = "true"
        if b.tipo == "exclusiveGateway": attr["isMarkerVisible"] = "true"
        s = ET.SubElement(piano, "{%s}BPMNShape" % BDI, attr)
        ET.SubElement(s, "{%s}Bounds" % DC, x=str(b.x), y=str(b.y), width=str(b.w), height=str(b.h))
    for f in processo.iter("{%s}sequenceFlow" % BPMN):
        pts = frecce.get(f.get("id"))
        if not pts: continue
        e = ET.SubElement(piano, "{%s}BPMNEdge" % BDI, id="e_" + f.get("id"), bpmnElement=f.get("id"))
        for x, y in pts:
            ET.SubElement(e, "{%s}waypoint" % DI, x=str(x + 80), y=str(y + 80))
    ET.indent(albero, space="  ")
    albero.write(path, encoding="UTF-8", xml_declaration=True)
    print("Disegno scritto in %s: %d forme" % (path, len(box)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
