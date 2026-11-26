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

/**
 * Le tappe del viaggio lungo una strada di frecce (id in ordine): il passo di partenza e poi l'arrivo di ogni
 * freccia, ciascuno con la frazione (0..1) della strada a cui il segnalino lo raggiunge.
 * flussi: [{ id, da, a, punti }]; restituisce [{ id, a }] (a = frazione di arrivo).
 */
export function tappeLungo(flussi, strada) {
  const tratti = strada.map(id => flussi.find(f => f.id === id)).filter(Boolean)
  if (!tratti.length) return []
  // la strada è una sola linea: conta anche il raccordo tra la fine di una freccia e l'inizio della successiva
  let ultimo = null
  const lunghezze = tratti.map(t => {
    let l = 0
    for (const p of t.punti || []) { if (ultimo) l += Math.hypot(p.x - ultimo.x, p.y - ultimo.y); ultimo = p }
    return l
  })
  const totale = lunghezze.reduce((s, l) => s + l, 0)
  const tappe = [{ id: tratti[0].da, a: 0, flusso: null }]
  let fatto = 0
  tratti.forEach((t, i) => {
    fatto += lunghezze[i]
    tappe.push({ id: t.a, a: totale ? fatto / totale : (i + 1) / tratti.length, flusso: t.id })
  })
  return tappe
}

/**
 * Il viaggio entra nei sottoprocessi: aggiunge una freccia "di passaggio" (id che inizia con ~) da ogni
 * sottoprocesso al suo evento di inizio, con un solo punto (il centro dell'inizio): la strada vi arriva dritta
 * dal bordo. sottoprocessi: [{ id, inizi: [{ id, x, y }] }].
 */
export function conPassaggi(flussi, sottoprocessi) {
  return [...flussi, ...sottoprocessi.flatMap(s => s.inizi.map(i => ({ id: `~${s.id}~${i.id}`, da: s.id, a: i.id, punti: [{ x: i.x, y: i.y }] })))]
}

/** Quanto dura il viaggio del segnalino: cresce con la strada (in unità del diagramma), tra 1,8 e 7 secondi. */
export const durataViaggio = (lunghezza) => Math.round(Math.min(7000, Math.max(1800, lunghezza * 2.4)))
