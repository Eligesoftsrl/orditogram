import { describe, expect, it } from 'vitest'
import { camminoFinoA, conPassaggi, durataViaggio, flussiPercorsi, puntoLungo, tappeLungo } from './percorsoBpmn.js'

// inizio → ricevi → valuta → (gw) → accogli | respingi → fine; integrazione torna da valuta a ricevi
const flussi = [
  { id: 'f1', da: 'inizio', a: 'ricevi' },
  { id: 'f2', da: 'ricevi', a: 'valuta' },
  { id: 'f3', da: 'valuta', a: 'gw' },
  { id: 'f4', da: 'gw', a: 'accogli' },
  { id: 'f5', da: 'gw', a: 'respingi' },
  { id: 'f6', da: 'gw', a: 'ricevi' },
  { id: 'f7', da: 'accogli', a: 'fine' },
  { id: 'f8', da: 'respingi', a: 'fine' }
]

describe('flussiPercorsi', () => {
  it('prende solo le frecce tra passi fatti o verso il passo attuale', () => {
    expect(flussiPercorsi(flussi, ['inizio', 'ricevi'], ['valuta'])).toEqual(['f1', 'f2'])
  })
  it('con un ritorno indietro include anche la freccia del ritorno', () => {
    expect(flussiPercorsi(flussi, ['inizio', 'ricevi', 'valuta', 'gw'], ['ricevi'])).toEqual(['f1', 'f2', 'f3', 'f6'])
  })
})

describe('camminoFinoA', () => {
  it('va dall\'inizio al passo attuale', () => {
    expect(camminoFinoA(flussi, ['inizio', 'ricevi'], ['valuta'])).toEqual(['f1', 'f2'])
  })
  it('a pratica conclusa arriva alla fine, senza girare nel ciclo', () => {
    expect(camminoFinoA(flussi, ['inizio', 'ricevi', 'valuta', 'gw', 'accogli', 'fine'], [])).toEqual(['f1', 'f2', 'f3', 'f4', 'f7'])
  })
  it('senza passi fatti non c\'è strada', () => {
    expect(camminoFinoA(flussi, [], ['ricevi'])).toEqual([])
  })
})

describe('puntoLungo', () => {
  const linea = [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }]
  it('trova il punto alla frazione della lunghezza', () => {
    expect(puntoLungo(linea, 0)).toEqual({ x: 0, y: 0 })
    expect(puntoLungo(linea, 0.5)).toEqual({ x: 10, y: 0 })
    expect(puntoLungo(linea, 0.75)).toEqual({ x: 10, y: 5 })
    expect(puntoLungo(linea, 1)).toEqual({ x: 10, y: 10 })
  })
  it('regge spezzate vuote o di un punto', () => {
    expect(puntoLungo([], 0.5)).toBeNull()
    expect(puntoLungo([{ x: 3, y: 4 }], 0.5)).toEqual({ x: 3, y: 4 })
  })
})

describe('tappeLungo', () => {
  const conPunti = [
    { id: 'f1', da: 'inizio', a: 'ricevi', punti: [{ x: 0, y: 0 }, { x: 100, y: 0 }] },
    { id: 'f2', da: 'ricevi', a: 'valuta', punti: [{ x: 100, y: 0 }, { x: 100, y: 100 }, { x: 400, y: 100 }] }
  ]
  it('parte dal primo passo e arriva a ogni passo alla sua frazione della strada', () => {
    expect(tappeLungo(conPunti, ['f1', 'f2'])).toEqual([
      { id: 'inizio', a: 0, flusso: null },
      { id: 'ricevi', a: 0.2, flusso: 'f1' },
      { id: 'valuta', a: 1, flusso: 'f2' }
    ])
  })
  it('senza strada non ci sono tappe', () => {
    expect(tappeLungo(conPunti, [])).toEqual([])
  })
  it('frecce senza lunghezza: tappe a passi uguali', () => {
    const corti = conPunti.map(f => ({ ...f, punti: [{ x: 0, y: 0 }] }))
    expect(tappeLungo(corti, ['f1', 'f2']).map(t => t.a)).toEqual([0, 0.5, 1])
  })
})

describe('durataViaggio', () => {
  it('resta tra 1,8 e 7 secondi', () => {
    expect(durataViaggio(10)).toBe(1800)
    expect(durataViaggio(1000)).toBe(2400)
    expect(durataViaggio(100000)).toBe(7000)
  })
})

describe('conPassaggi', () => {
  // inizio → istruttoria (sottoprocesso: iInizio → esame) ; la pratica è ferma su esame, dentro
  const fuori = [{ id: 'f1', da: 'inizio', a: 'istruttoria', punti: [{ x: 0, y: 0 }, { x: 100, y: 0 }] },
    { id: 'i1', da: 'iInizio', a: 'esame', punti: [{ x: 130, y: 0 }, { x: 200, y: 0 }] }]
  const tutti = conPassaggi(fuori, [{ id: 'istruttoria', inizi: [{ id: 'iInizio', x: 120, y: 0 }] }])
  it('la strada entra nel sottoprocesso e arriva al passo dentro', () => {
    expect(camminoFinoA(tutti, ['inizio', 'istruttoria', 'iInizio'], ['esame'])).toEqual(['f1', '~istruttoria~iInizio', 'i1'])
  })
  it('le tappe contano anche il raccordo dal bordo al centro dell\'inizio', () => {
    expect(tappeLungo(tutti, ['f1', '~istruttoria~iInizio', 'i1']).map(t => t.a)).toEqual([0, 0.5, 0.6, 1])
  })
})
