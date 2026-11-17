import { describe, expect, it } from 'vitest'
import { sonoEsiti, tonoEsito } from './esiti.js'

describe('tonoEsito', () => {
  it('riconosce gli esiti positivi', () => {
    for (const t of ['Accolta', 'Sì', 'Approvato', 'Accesso concesso', 'Conferma']) expect(tonoEsito(t)).toBe('positivo')
  })
  it('riconosce gli esiti negativi, anche con "non" davanti a una parola positiva', () => {
    for (const t of ['Respinta', 'No', 'Non accolta', 'Rigetto', 'Domanda inammissibile', 'Diniego']) expect(tonoEsito(t)).toBe('negativo')
  })
  it('riconosce le attese', () => {
    for (const t of ['Serve un\'integrazione', 'Sospendi', 'Accolta parzialmente']) expect(tonoEsito(t)).not.toBe('neutro')
    expect(tonoEsito('Richiesta di chiarimenti')).toBe('attesa')
  })
  it('lascia neutre le opzioni qualsiasi', () => {
    for (const t of ['Roma', 'Posta', 'Notifica', 'Negozio', '', undefined]) expect(tonoEsito(t)).toBe('neutro')
  })
})

describe('opzioni del processo di esempio', () => {
  it('esame e decisione dell\'accesso agli atti', () => {
    expect(['Accoglibile', 'Serve integrazione', 'Ci sono controinteressati', 'Da respingere'].map(tonoEsito))
      .toEqual(['positivo', 'attesa', 'neutro', 'negativo'])
    expect(['Accolta', 'Accolta in parte', 'Respinta'].map(tonoEsito)).toEqual(['positivo', 'attesa', 'negativo'])
  })
})

describe('sonoEsiti', () => {
  it('vale per una scelta d\'esito, non per un elenco qualsiasi', () => {
    expect(sonoEsiti(['Accolta', 'Respinta', 'Serve un\'integrazione'])).toBe(true)
    expect(sonoEsiti(['Email', 'PEC', 'Posta'])).toBe(false)
    expect(sonoEsiti(['Sì', 'Roma'])).toBe(false)
  })
})
