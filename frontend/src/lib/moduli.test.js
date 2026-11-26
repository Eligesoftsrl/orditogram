import { describe, expect, it } from 'vitest'
import { formatiDisponibili, moduloAcceso } from './moduli.js'
import { FORMATI } from './formRegole.js'

describe('moduli', () => {
  it('senza informazioni dal server sono tutti accesi', () => {
    expect(moduloAcceso(null, 'italia')).toBe(true)
    expect(moduloAcceso({}, 'pa')).toBe(true)
    expect(moduloAcceso({ moduli: ['pa'] }, 'italia')).toBe(false)
  })
  it('con Italia spenta i formati italiani spariscono, tranne quello già scelto', () => {
    const spenta = { moduli: ['firma-digitale', 'pa'] }
    expect(formatiDisponibili(FORMATI, spenta).map(f => f.id)).toEqual(['email', 'iban', 'espressione'])
    expect(formatiDisponibili(FORMATI, spenta, 'cap').map(f => f.id)).toContain('cap')
    expect(formatiDisponibili(FORMATI, { moduli: ['italia'] })).toHaveLength(FORMATI.length)
  })
})
