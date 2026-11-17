import { describe, expect, it } from 'vitest'
import { durata, posizioneAttuale, tempiDelPercorso } from './percorso.js'

const h = (n) => n * 3600e3
const t0 = new Date('2026-11-10T09:00:00Z').getTime()

describe('durata', () => {
  it('scrive la durata in parole', () => {
    expect(durata(20e3)).toBe('meno di un minuto')
    expect(durata(60e3)).toBe('1 minuto')
    expect(durata(h(5))).toBe('5 ore')
    expect(durata(h(24))).toBe('1 giorno')
    expect(durata(h(72))).toBe('3 giorni')
    expect(durata(null)).toBe('')
  })
})

describe('tempiDelPercorso', () => {
  const percorso = [
    { nome: 'Avvio', stato: 'fatto', quando: new Date(t0).toISOString() },
    { nome: 'Verifica', stato: 'fatto', quando: new Date(t0 + h(48)).toISOString() },
    { nome: 'Decisione', stato: 'corrente', quando: null }
  ]
  it('misura i passi fatti e quello in corso', () => {
    expect(tempiDelPercorso(percorso, t0 + h(53))).toEqual([
      { nome: 'Verifica', durata: '2 giorni', inCorso: false },
      { nome: 'Decisione', durata: '5 ore', inCorso: true }
    ])
  })
  it('usa "dal" per il passo in corso quando c\'è', () => {
    expect(tempiDelPercorso(percorso, t0 + h(53), new Date(t0 + h(52)).toISOString())[1].durata).toBe('1 ora')
  })
  it('ignora fermate senza data', () => {
    expect(tempiDelPercorso([{ nome: 'Avvio', stato: 'fatto', quando: null }, { nome: 'X', stato: 'corrente' }], t0)).toEqual([
      { nome: 'X', durata: '', inCorso: true }
    ])
  })
})

describe('posizioneAttuale', () => {
  it('trova il passo corrente o l\'ultimo', () => {
    expect(posizioneAttuale([{ stato: 'fatto' }, { stato: 'corrente' }])).toBe(1)
    expect(posizioneAttuale([{ stato: 'fatto' }, { stato: 'fine' }])).toBe(1)
    expect(posizioneAttuale([])).toBe(0)
  })
})
