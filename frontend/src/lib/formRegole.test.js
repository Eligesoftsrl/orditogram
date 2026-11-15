import { describe, expect, it } from 'vitest'
import { codiceFiscaleValido, controlla, emailValida, descriviRegole, erroriForm, ibanValido, partitaIvaValida } from './formRegole.js'

const OGGI = '2026-11-15'

describe('regole dei campi (uguali al backend)', () => {
  it('numeri e date', () => {
    const importo = { type: 'number', min: 10, max: '1000.5' }
    expect(controlla(importo, '10')).toBeNull()
    expect(controlla(importo, '9.99')).toBe('deve essere almeno 10')
    expect(controlla(importo, 2000)).toBe('deve essere al massimo 1000,5')
    const scadenza = { type: 'date', min: 'oggi', max: '2026-12-31' }
    expect(controlla(scadenza, '2026-11-15', OGGI)).toBeNull()
    expect(controlla(scadenza, '2026-11-14', OGGI)).toBe('non può essere prima del giorno di oggi')
    expect(controlla(scadenza, '2027-01-01', OGGI)).toBe('non può essere dopo il 31/12/2026')
  })

  it('lunghezza del testo', () => {
    const note = { type: 'textarea', minLunghezza: 3, maxLunghezza: 5 }
    expect(controlla(note, 'ab')).toBe('deve avere almeno 3 caratteri')
    expect(controlla(note, 'abcdef')).toBe('può avere al massimo 5 caratteri')
    expect(controlla(note, 'àèìòù')).toBeNull()
    expect(controlla(note, '')).toBeNull()
  })

  it('formati italiani', () => {
    expect(codiceFiscaleValido('RSSMRA80A01H501U')).toBe(true)
    expect(codiceFiscaleValido('RSSMRA80A01H501X')).toBe(false)
    expect(codiceFiscaleValido('00743110157')).toBe(true)
    expect(partitaIvaValida('00743110158')).toBe(false)
    expect(ibanValido('IT60X0542811101000000123456')).toBe(true)
    expect(ibanValido('IT60X0542811101000000123457')).toBe(false)
    expect(controlla({ type: 'text', formato: 'codiceFiscale' }, 'rssmra80a01h501u')).toBeNull()
    expect(controlla({ type: 'text', formato: 'iban' }, 'IT60 X054 2811 1010 0000 0123 456')).toBeNull()
    expect(controlla({ type: 'text', formato: 'cap' }, '1018')).toBe('deve essere un CAP di 5 cifre')
    expect(controlla({ type: 'text', formato: 'email' }, 'mario@')).toBe('deve essere un indirizzo email valido')
    expect(emailValida('protocollo@pec.comune.it')).toBe(true)
    for (const no of ['@x.it', 'a@@b.it', 'a@b@c.it', 'a b@c.it', 'a@b.c', 'a@.it', 'a@bit']) expect(emailValida(no)).toBe(false)
    expect(controlla({ type: 'text', formato: 'telefono' }, '+39 06 1234567')).toBeNull()
  })

  it('espressione dell\'analista', () => {
    const f = { type: 'text', formato: 'espressione', espressione: '[0-9]{4}/[0-9]+', messaggioFormato: 'scrivi anno/numero' }
    expect(controlla(f, '2026/123')).toBeNull()
    expect(controlla(f, '123')).toBe('scrivi anno/numero')
    expect(controlla({ ...f, espressione: '([a-z' }, 'x')).toBeNull()
  })

  it('solo campi visibili e da compilare', () => {
    const campi = [
      { name: 'importo', type: 'number', min: 1 },
      { name: 'pec', type: 'text', formato: 'email' },
      { name: 'protocollo', type: 'text', readonly: true, formato: 'cap' }
    ]
    expect(erroriForm(campi, { importo: '0', pec: 'no', protocollo: 'x' }, new Set(['pec'])))
      .toEqual({ importo: 'deve essere almeno 1' })
    expect(erroriForm([{ name: 'a', required: true }, { name: 'ok', type: 'checkbox', required: true }], { a: ' ' }))
      .toEqual({ a: 'è obbligatorio', ok: 'va spuntato per proseguire' })
    expect(descriviRegole({ min: 1, max: 10 })).toBe('da 1, fino a 10')
    expect(descriviRegole({ formato: 'codiceFiscale', maxLunghezza: 16 })).toBe('al massimo 16 caratteri, codice fiscale')
  })
})
