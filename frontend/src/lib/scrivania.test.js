import { describe, expect, it } from 'vitest'
import { inPrimoPiano, tonoScadenza, valoreContatore } from './scrivania.js'

const ora = new Date('2026-11-18T12:00:00Z').getTime()
const t = (id, quando, creata = '2026-11-01T00:00:00Z', tipo = 'scadenza') =>
  ({ id, createdDate: creata, scadenze: quando ? [{ tipo, quando, nome: 'Scadenza' }] : [] })

describe('inPrimoPiano', () => {
  it('mette prima le scadenze più vicine, poi le attività più vecchie senza scadenza', () => {
    const lista = [
      t('senza-nuova', null, '2026-11-10T00:00:00Z'),
      t('lontana', '2026-12-10T00:00:00Z'),
      t('scaduta', '2026-11-17T00:00:00Z'),
      t('senza-vecchia', null, '2026-11-02T00:00:00Z'),
      t('vicina', '2026-11-19T00:00:00Z')
    ]
    expect(inPrimoPiano(lista, 5).map(x => x.id)).toEqual(['scaduta', 'vicina', 'lontana', 'senza-vecchia', 'senza-nuova'])
  })
  it('ignora solleciti e attese e non cambia la lista ricevuta', () => {
    const lista = [t('a', null), t('b', '2026-11-17T00:00:00Z', undefined, 'sollecito')]
    const copia = [...lista]
    expect(inPrimoPiano(lista, 1)).toHaveLength(1)
    expect(lista).toEqual(copia)
  })
})

describe('tonoScadenza', () => {
  it('distingue scaduta, entro due giorni e lontana', () => {
    expect(tonoScadenza(t('x', '2026-11-18T11:00:00Z'), ora)).toBe('mattone')
    expect(tonoScadenza(t('x', '2026-11-19T11:00:00Z'), ora)).toBe('zafferano')
    expect(tonoScadenza(t('x', '2026-11-28T11:00:00Z'), ora)).toBe('neutro')
    expect(tonoScadenza(t('x', null), ora)).toBe('neutro')
  })
})

describe('valoreContatore', () => {
  it('parte da zero, sale rallentando e arriva esatto', () => {
    expect(valoreContatore(100, 0)).toBe(0)
    expect(valoreContatore(100, 350)).toBeGreaterThan(50)
    expect(valoreContatore(100, 700)).toBe(100)
    expect(valoreContatore(100, 9999)).toBe(100)
  })
  it('senza durata o con valori non numerici restituisce il valore finale', () => {
    expect(valoreContatore(7, 0, 0)).toBe(7)
    expect(valoreContatore('—', 10)).toBe('—')
  })
})
