import { describe, expect, it } from 'vitest'
import { azioniPersona, erroreNuovaPassword, statoPersona } from './referente.js'

describe('azioniPersona', () => {
  it('niente azioni su sé stesso e sugli altri referenti', () => {
    expect(azioniPersona({ io: true })).toEqual({ attiva: false, togli: false })
    expect(azioniPersona({ referente: true })).toEqual({ attiva: false, togli: false })
  })
  it('chi è anche in altre organizzazioni si può solo togliere', () => {
    expect(azioniPersona({ altreOrganizzazioni: true })).toEqual({ attiva: false, togli: true })
    expect(azioniPersona({})).toEqual({ attiva: true, togli: true })
  })
})

describe('statoPersona', () => {
  it('disattivata, in attesa o attiva', () => {
    expect(statoPersona({ attivo: false, inAttesa: true }).testo).toBe('Disattivata')
    expect(statoPersona({ attivo: true, inAttesa: true }).testo).toBe('Non ancora entrata')
    expect(statoPersona({ attivo: true, inAttesa: false }).testo).toBe('Attiva')
  })
})

describe('erroreNuovaPassword', () => {
  it('stesse regole del server', () => {
    expect(erroreNuovaPassword('corta', 'corta', 'anna')).toMatch(/12/)
    expect(erroreNuovaPassword('anna-lunga-password', 'anna-lunga-password', 'Anna')).toMatch(/nome utente/)
    expect(erroreNuovaPassword('una-password-lunga', 'una-password-lunga', 'anna', 'una-password-lunga')).toMatch(/diversa/)
    expect(erroreNuovaPassword('una-password-lunga', 'altra', 'anna')).toMatch(/coincidono/)
    expect(erroreNuovaPassword('una-password-lunga', 'una-password-lunga', 'anna', 'vecchia')).toBe('')
  })
})
