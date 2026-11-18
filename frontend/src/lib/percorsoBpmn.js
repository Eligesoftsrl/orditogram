/**
 * Il cammino di una pratica sul diagramma BPMN: quali frecce ha già percorso e la strada che il segnalino
 * fa, ogni tanto, dall'inizio fino al passo dove la pratica è ferma.
 * flussi: [{ id, da, a }] (sequence flow con sorgente e destinazione); fatti e attivi: id delle attività.
 */

/** Le frecce percorse: partono da un passo fatto e arrivano a un passo fatto o attuale. */
export function flussiPercorsi(flussi, fatti, attivi) {
  const f = new Set(fatti), arrivo = new Set([...fatti, ...attivi])
  return flussi.filter(x => f.has(x.da) && arrivo.has(x.a)).map(x => x.id)
}

/**
 * La strada più breve, tra le frecce percorse, da un punto di partenza fino a un passo attuale
 * (se la pratica è conclusa, fino a un passo da cui non esce nessuna freccia percorsa).
 * Restituisce gli id delle frecce in ordine; vuota se non c'è una strada.
 */
export function camminoFinoA(flussi, fatti, attivi) {
  const percorse = new Set(flussiPercorsi(flussi, fatti, attivi))
  const usati = flussi.filter(x => percorse.has(x.id))
  const entrano = new Set(usati.map(x => x.a))
  const escono = new Set(usati.map(x => x.da))
  const partenze = fatti.filter(n => !entrano.has(n))
  const mete = new Set(attivi.length ? attivi : fatti.filter(n => !escono.has(n)))
  const coda = partenze.map(n => ({ nodo: n, strada: [] }))
  const visti = new Set(partenze)
  while (coda.length) {
    const { nodo, strada } = coda.shift()
    if (mete.has(nodo) && strada.length) return strada
    for (const x of usati.filter(u => u.da === nodo)) {
      if (visti.has(x.a)) continue
      visti.add(x.a)
      coda.push({ nodo: x.a, strada: [...strada, x.id] })
    }
  }
  return []
}

/** Il punto di una spezzata (waypoints [{x,y}]) alla frazione q (0..1) della sua lunghezza. */
export function puntoLungo(punti, q) {
  if (!punti?.length) return null
  if (punti.length === 1) return { x: punti[0].x, y: punti[0].y }
  const tratti = []
  let totale = 0
  for (let i = 1; i < punti.length; i++) {
    const l = Math.hypot(punti[i].x - punti[i - 1].x, punti[i].y - punti[i - 1].y)
    tratti.push(l); totale += l
  }
  let resto = Math.min(1, Math.max(0, q)) * totale
  for (let i = 0; i < tratti.length; i++) {
    if (resto <= tratti[i] || i === tratti.length - 1) {
      const t = tratti[i] ? Math.min(1, resto / tratti[i]) : 0
      return { x: punti[i].x + (punti[i + 1].x - punti[i].x) * t, y: punti[i].y + (punti[i + 1].y - punti[i].y) * t }
    }
    resto -= tratti[i]
  }
  return { ...punti[punti.length - 1] }
}
