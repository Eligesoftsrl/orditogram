import { describe, expect, it } from 'vitest'
import { camminoFinoA, flussiPercorsi, puntoLungo } from './percorsoBpmn.js'

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
