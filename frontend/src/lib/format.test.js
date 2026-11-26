import { describe, expect, it } from 'vitest'
import { dataOraCompleta, fa, giorno, quando } from './format.js'

describe('date', () => {
  it('legge le date dei form', () => {
    expect(giorno('2010-03-01')).toBe('1 marzo 2010')
  })

  it('un valore che non è una data non rompe la pagina', () => {
    const vecchio = 'Mon Feb 15 01:00:00 CET 2010'
    expect(giorno(vecchio)).toBe(vecchio)
    expect(quando(vecchio)).toBe(vecchio)
    expect(fa(vecchio)).toBe(vecchio)
    expect(dataOraCompleta(vecchio)).toBe(vecchio)
  })
})
