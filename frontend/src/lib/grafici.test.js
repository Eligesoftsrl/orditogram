import { describe, expect, it } from 'vitest'
import { altezzaBarra, etichettaGiorno, etichettaSettimana, fasceAvanzamento } from './grafici.js'

describe('altezzaBarra', () => {
  it('è proporzionale al massimo', () => {
    expect(altezzaBarra(5, 10, 100)).toBe('50px')
    expect(altezzaBarra(10, 10, 100)).toBe('100px')
  })
  it('zero resta zero, un valore piccolo resta visibile', () => {
    expect(altezzaBarra(0, 10, 100)).toBe('0px')
    expect(altezzaBarra(1, 1000, 100)).toBe('3px')
    expect(altezzaBarra(4, 0, 100)).toBe('0px')
  })
})

describe('etichette', () => {
  it('settimana e giorni in italiano', () => {
    expect(etichettaSettimana('2026-11-16')).toBe('16 nov')
    expect(etichettaGiorno('2026-11-18', 0)).toBe('oggi')
    expect(etichettaGiorno('2026-11-19', 1)).toBe('domani')
    expect(etichettaGiorno('2026-11-20', 2)).toBe('ven 20')
  })
})

describe('fasceAvanzamento', () => {
  it('ordina le fasce e mette zero dove manca', () => {
    expect(fasceAvanzamento({ da0a25: 2, da75a100: 1 }).map(f => f.n)).toEqual([2, 0, 0, 1])
    expect(fasceAvanzamento().map(f => f.etichetta)).toEqual(['0–25%', '25–50%', '50–75%', '75–95%'])
  })
})
